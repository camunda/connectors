/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage;
import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.getKafkaProperties;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnectorConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaConnectorConsumer.class);
  public static ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(DefaultScalaModule$.MODULE$)
          .registerModule(new JavaTimeModule())
          // deserialize unknown types as empty objects
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
          .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
  private final InboundConnectorContext context;
  private final ExecutorService executorService;
  private final RetryPolicy<Object> retryPolicy;
  private final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction;
  public CompletableFuture<?> future;
  Consumer<Object, Object> consumer;
  KafkaConnectorProperties elementProps;
  boolean shouldLoop = true;
  private Health consumerStatus = Health.unknown();
  private ObjectReader avroObjectReader;

  public KafkaConnectorConsumer(
      final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction,
      final InboundConnectorContext connectorContext,
      final KafkaConnectorProperties elementProps,
      final RetryPolicy<Object> retryPolicy) {
    this.consumerCreatorFunction = consumerCreatorFunction;
    this.context = connectorContext;
    this.elementProps = elementProps;
    this.executorService = Executors.newSingleThreadExecutor();
    this.retryPolicy = retryPolicy;
  }

  public void startConsumer() {
    if (elementProps.avro() != null) {
      Schema schema = new Schema.Parser().setValidate(true).parse(elementProps.avro().schema());
      AvroSchema avroSchema = new AvroSchema(schema);
      AvroMapper avroMapper = new AvroMapper();
      avroObjectReader = avroMapper.reader(avroSchema);
    }

    CheckedSupplier<Void> retryableFutureSupplier =
        () -> {
          try {
            prepareConsumer();
            consume();
            return null;
          } catch (Exception ex) {
            LOG.error("Consumer loop failure, retry pending: {}", ex.getMessage());
            try {
              consumer.close();
            } catch (Exception e) {
              LOG.error(
                  "Failed to close consumer before retrying, reason: {}. "
                      + "This error will be ignored. If the consumer is still running, it will be disconnected after max.poll.interval.ms.",
                  e.getMessage());
            }
            throw ex;
          }
        };

    future =
        Failsafe.with(retryPolicy)
            .with(executorService)
            .getAsync(retryableFutureSupplier)
            .exceptionally(
                (e) -> {
                  shouldLoop = false;
                  throw new RuntimeException(
                      "Consumer loop failed, retries exhausted: " + e.getMessage(), e);
                });
  }

  private void prepareConsumer() {
    try {
      this.consumer = consumerCreatorFunction.apply(getKafkaProperties(elementProps, context));
      String topicName = elementProps.topic().topicName();
      consumer.subscribe(
          List.of(topicName),
          new OffsetUpdateRequiredListener(topicName, consumer, elementProps.offsets()));
      reportUp();
    } catch (Exception ex) {
      LOG.error("Failed to initialize connector: {}", ex.getMessage());
      context.log(
          Activity.level(Severity.ERROR)
              .tag("Subscription")
              .message("Failed to initialize connector: " + ex.getMessage()));
      context.reportHealth(Health.down(ex));
      throw ex;
    }
  }

  public void consume() {
    while (shouldLoop) {
      try {
        pollAndPublish();
        reportUp();
      } catch (Exception ex) {
        reportDown(ex);
        throw ex;
      }
    }
    LOG.debug("Kafka inbound loop finished");
  }

  private void pollAndPublish() {
    LOG.trace("Polling the topics: {}", this.consumer.assignment());
    ConsumerRecords<Object, Object> records = this.consumer.poll(Duration.ofMillis(500));
    for (ConsumerRecord<Object, Object> record : records) {
      handleMessage(record);
    }
    if (!records.isEmpty()) {
      this.consumer.commitSync();
    }
  }

  private void handleMessage(ConsumerRecord<Object, Object> record) {
    LOG.trace("Kafka message received: key = {}, value = {}", record.key(), record.value());
    context.log(
        Activity.level(Severity.INFO)
            .tag("Message")
            .message("Received message with key : " + record.key()));
    var reader = avroObjectReader != null ? avroObjectReader : objectMapper.reader();
    var mappedMessage = convertConsumerRecordToKafkaInboundMessage(record, reader);
    var result = context.correlateWithResult(mappedMessage);
    handleCorrelationResult(result);
  }

  private void handleCorrelationResult(CorrelationResult result) {
    switch (result) {
      case Success ignored -> LOG.debug("Message correlated successfully");
      case Failure failure -> {
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored -> {
            LOG.debug("Message not correlated, reason: {}. Offset will not be committed", failure);
            throw new RuntimeException(
                "Message cannot be processed: " + failure.getClass().getSimpleName());
          }
          case Ignore ignored -> LOG.debug(
              "Message not correlated, but the error is ignored. Offset will be committed");
        }
      }
    }
  }

  public void stopConsumer() throws ExecutionException, InterruptedException {
    this.shouldLoop = false;
    if (this.future != null && !this.future.isDone()) {
      this.future.get();
    }
    this.consumer.close();
    if (this.executorService != null) {
      this.executorService.shutdownNow();
    }
  }

  private void reportUp() {
    var details = new HashMap<String, Object>();
    details.put("group-id", consumer.groupMetadata().groupId());
    details.put("group-instance-id", consumer.groupMetadata().groupInstanceId().orElse("unknown"));
    details.put("group-generation-id", consumer.groupMetadata().generationId());
    var newStatus = Health.up(details);
    if (!newStatus.equals(consumerStatus)) {
      consumerStatus = newStatus;
      context.reportHealth(Health.up(details));
      LOG.info(
          "Consumer status changed to UP, deduplication ID: {}",
          context.getDefinition().deduplicationId());
    }
  }

  private void reportDown(Throwable error) {
    var newStatus = Health.down(error);
    context.log(
        Activity.level(Severity.ERROR)
            .tag("Kafka Consumer")
            .message("Kafka Consumer status changed to DOWN: " + newStatus));
    if (!newStatus.equals(consumerStatus)) {
      consumerStatus = newStatus;
      context.reportHealth(Health.down(error));
      LOG.error(
          "Kafka Consumer status changed to DOWN, deduplication ID: {}",
          context.getDefinition().deduplicationId(),
          error);
    }
  }
}
