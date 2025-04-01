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
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
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
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnectorConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaConnectorConsumer.class);

  private final InboundConnectorContext context;

  private final ExecutorService executorService;

  public CompletableFuture<?> future;

  Consumer<String, Object> consumer;

  KafkaConnectorProperties elementProps;

  private Health consumerStatus = Health.unknown();

  private final RetryPolicy<Object> retryPolicy;

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
  private final Function<Properties, Consumer<String, Object>> consumerCreatorFunction;
  boolean shouldLoop = true;
  private ObjectReader avroObjectReader;

  public KafkaConnectorConsumer(
      final Function<Properties, Consumer<String, Object>> consumerCreatorFunction,
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
    if (elementProps.getAvro() != null) {
      var schemaString = StringEscapeUtils.unescapeJson(elementProps.getAvro().schema());
      Schema schema = new Schema.Parser().setValidate(true).parse(schemaString);
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
                  return null;
                });
  }

  private void prepareConsumer() {
    try {
      this.consumer = consumerCreatorFunction.apply(getKafkaProperties(elementProps, context));
      String topicName = elementProps.getTopic().getTopicName();
      consumer.subscribe(
          List.of(topicName),
          new OffsetUpdateRequiredListener(topicName, consumer, elementProps.getOffsets()));
      reportUp();
    } catch (Exception ex) {
      LOG.error("Failed to initialize connector: {}", ex.getMessage());
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
    LOG.debug("Polling the topics: {}", this.consumer.assignment());
    ConsumerRecords<String, Object> records = this.consumer.poll(Duration.ofMillis(500));
    for (ConsumerRecord<String, Object> record : records) {
      handleMessage(record);
    }
    if (!records.isEmpty()) {
      this.consumer.commitSync();
    }
  }

  private void handleMessage(ConsumerRecord<String, Object> record) {
    LOG.trace("Kafka message received: key = {}, value = {}", record.key(), record.value());
    var reader = avroObjectReader != null ? avroObjectReader : objectMapper.reader();
    var mappedMessage = convertConsumerRecordToKafkaInboundMessage(record, reader);
    this.context.correlate(mappedMessage);
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
          "Consumer status changed to UP, process {}, version {}, element {} ",
          context.getDefinition().bpmnProcessId(),
          context.getDefinition().version(),
          context.getDefinition().elementId());
    }
  }

  private void reportDown(Throwable error) {
    var newStatus = Health.down(error);
    if (!newStatus.equals(consumerStatus)) {
      consumerStatus = newStatus;
      context.reportHealth(Health.down(error));
      LOG.error(
          "Kafka Consumer status changed to DOWN, process {}, version {}, element {}",
          context.getDefinition().bpmnProcessId(),
          context.getDefinition().version(),
          context.getDefinition().elementId(),
          error);
    }
  }
}
