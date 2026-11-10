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
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.kafka.model.schema.AvroInlineSchemaStrategy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.config.ConfigDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnectorConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaConnectorConsumer.class);
  static final String COORDINATOR_METRIC_GROUP = "consumer-coordinator-metrics";
  static final String LAST_HEARTBEAT_METRIC = "last-heartbeat-seconds-ago";
  public static ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new DefaultScalaModule())
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
  KafkaConnectorProperties elementProps;
  boolean shouldLoop = true;
  private Health consumerStatus = Health.unknown();
  private Duration groupMembershipTimeout;
  private Duration sessionTimeout;
  private MetricName lastHeartbeatMetricName;
  private Instant notInGroupSince;
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
    if (elementProps.schemaStrategy() instanceof AvroInlineSchemaStrategy strategy) {
      Schema schema = new Schema.Parser().parse(strategy.schema());
      AvroSchema avroSchema = new AvroSchema(schema);
      AvroMapper avroMapper = new AvroMapper();
      avroObjectReader = avroMapper.reader(avroSchema);
    }

    CheckedSupplier<Void> retryableFutureSupplier =
        () -> {
          try (Consumer<Object, Object> consumer = prepareConsumer()) {
            consume(consumer);
            return null;
          } catch (Exception ex) {
            LOG.warn("Consumer loop failure, retry pending: {}", ex.getMessage(), ex);
            throw ex;
          }
        };

    future =
        Failsafe.with(retryPolicy)
            .with(executorService)
            .getAsync(retryableFutureSupplier)
            .exceptionally(e -> {
              // Errors (e.g. OutOfMemoryError) are not retried and bypass the reportDown in
              // consume(), so the connector would otherwise stay UP while no longer consuming
              if (shouldLoop) {
                reportDown(e);
              }
              shouldLoop = false;
              throw new RuntimeException(
                  "Consumer loop failed, retries exhausted: " + e.getMessage(), e);
            });
  }

  private Consumer<Object, Object> prepareConsumer() {
    try {
      var kafkaProps = getKafkaProperties(elementProps, context);
      var consumer = consumerCreatorFunction.apply(kafkaProps);
      groupMembershipTimeout = intConfig(kafkaProps, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG);
      sessionTimeout = intConfig(kafkaProps, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG);
      lastHeartbeatMetricName = null;
      notInGroupSince = null;
      String topicName = elementProps.topic().topicName();
      consumer.subscribe(
          List.of(topicName),
          new OffsetUpdateRequiredListener(topicName, consumer, elementProps.offsets()));
      reportGroupHealth(consumer);

      return consumer;
    } catch (Exception ex) {
      context.log(activity ->
          activity
              .withSeverity(Severity.ERROR)
              .withTag(ActivityLogTag.CONSUMER)
              .withMessage("Failed to initialize connector", ex));
      context.reportHealth(Health.down(ex));
      throw ex;
    }
  }

  public void consume(Consumer<Object, Object> consumer) {
    while (shouldLoop) {
      try {
        pollAndPublish(consumer);
        reportGroupHealth(consumer);
      } catch (Exception ex) {
        reportDown(ex);
        throw ex;
      }
    }
    LOG.debug("Kafka inbound loop finished");
  }

  private void pollAndPublish(Consumer<Object, Object> consumer) {
    LOG.trace("Polling the topics: {}", consumer.assignment());
    ConsumerRecords<Object, Object> records = consumer.poll(Duration.ofMillis(500));
    for (ConsumerRecord<Object, Object> record : records) {
      handleMessage(record);
    }
    if (!records.isEmpty()) {
      consumer.commitSync();
    }
  }

  private void handleMessage(ConsumerRecord<Object, Object> record) {
    LOG.trace("Kafka message received: key = {}, value = {}", record.key(), record.value());
    context.log(activity ->
        activity
            .withSeverity(Severity.INFO)
            .withTag(ActivityLogTag.MESSAGE)
            .withMessage("Received message with key : " + record.key()));
    var reader = avroObjectReader != null ? avroObjectReader : objectMapper.reader();
    var mappedMessage = convertConsumerRecordToKafkaInboundMessage(record, reader);
    String messageId = record.topic() + "-" + record.partition() + "-" + record.offset();
    var result =
        context.correlate(
            CorrelationRequest.builder().variables(mappedMessage).messageId(messageId).build());
    handleCorrelationResult(result);
  }

  private void handleCorrelationResult(CorrelationResult result) {
    switch (result) {
      case Success ignored -> LOG.debug("Message correlated successfully");
      case Failure failure -> {
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored -> {
            throw new RuntimeException(
                "Message cannot be processed: "
                    + failure.message()
                    + ". Offset will not be committed.");
          }
          case Ignore ignored ->
              context.log(activity ->
                  activity
                      .withSeverity(Severity.INFO)
                      .withTag(ActivityLogTag.MESSAGE)
                      .withMessage(
                          "Message not correlated, but the error is ignored. Offset will be committed"));
        }
      }
    }
  }

  public void stopConsumer() {
    this.shouldLoop = false;
    if (this.future != null && !this.future.isDone()) {
      try {
        this.future.get(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        LOG.warn("Timeout while waiting for retryableFuture to stop", e);
      }
    }
    if (this.executorService != null) {
      this.executorService.shutdownNow();
    }
  }

  private static Duration intConfig(Properties kafkaProps, String name) {
    Object value =
        kafkaProps.getOrDefault(name, ConsumerConfig.configDef().defaultValues().get(name));
    return Duration.ofMillis((Integer) ConfigDef.parseType(name, value, ConfigDef.Type.INT));
  }

  // A consumer that is not in its group does not receive any message, even though poll() keeps
  // returning without error while the client retries in the background
  private void reportGroupHealth(Consumer<Object, Object> consumer) {
    var groupMetadata = consumer.groupMetadata();
    int generation = groupMetadata.generationId();
    var details = new HashMap<String, Object>();
    details.put("group-id", groupMetadata.groupId());
    details.put("group-instance-id", groupMetadata.groupInstanceId().orElse("unknown"));
    details.put("group-generation-id", generation);

    if (isGroupMember(consumer, generation)) {
      notInGroupSince = null;
      reportHealth(Health.up(details), Severity.INFO, details);
      return;
    }
    if (notInGroupSince == null) {
      notInGroupSince = Instant.now();
    }
    // The broker may hold a join for up to the rebalance timeout, which is max.poll.interval.ms
    if (Duration.between(notInGroupSince, Instant.now()).compareTo(groupMembershipTimeout) < 0) {
      reportHealth(Health.unknown(details), Severity.INFO, details);
    } else {
      var error =
          new Health.Error(
              "NOT_IN_CONSUMER_GROUP",
              String.format(
                  "Kafka consumer has not been a member of consumer group '%s' for %d ms (%s)."
                      + " It cannot receive messages. Check that the brokers and the group"
                      + " coordinator are reachable with the configured connection settings; the"
                      + " connector runtime logs contain the Kafka client's connection errors.",
                  groupMetadata.groupId(),
                  groupMembershipTimeout.toMillis(),
                  ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
      reportHealth(Health.down(error, details), Severity.ERROR, details);
    }
  }

  // Joined generations start at 1. The classic consumer's groupMetadata() keeps the last
  // generation after the membership is lost, so a heartbeat sent to the coordinator within the
  // session timeout is what confirms that the consumer is still a member. Heartbeats are only
  // sent while the member is stable, and stop once the coordinator stops answering.
  private boolean isGroupMember(Consumer<Object, Object> consumer, int generation) {
    if (generation <= 0) {
      return false;
    }
    Optional<Double> lastHeartbeatSecondsAgo = lastHeartbeatSecondsAgo(consumer);
    if (lastHeartbeatSecondsAgo.isEmpty()) {
      // Consumers without coordinator metrics (e.g. other Consumer implementations)
      return true;
    }
    // -1 means no heartbeat has been sent since joining, which does not confirm membership
    double secondsAgo = lastHeartbeatSecondsAgo.get();
    return secondsAgo >= 0 && secondsAgo * 1000 <= sessionTimeout.toMillis();
  }

  private Optional<Double> lastHeartbeatSecondsAgo(Consumer<Object, Object> consumer) {
    Map<MetricName, ? extends Metric> metrics = consumer.metrics();
    if (lastHeartbeatMetricName == null) {
      lastHeartbeatMetricName =
          metrics.keySet().stream()
              .filter(name -> LAST_HEARTBEAT_METRIC.equals(name.name()))
              .filter(name -> COORDINATOR_METRIC_GROUP.equals(name.group()))
              .findFirst()
              .orElse(null);
      if (lastHeartbeatMetricName == null) {
        return Optional.empty();
      }
    }
    Metric metric = metrics.get(lastHeartbeatMetricName);
    if (metric != null && metric.metricValue() instanceof Number value) {
      return Optional.of(value.doubleValue());
    }
    return Optional.empty();
  }

  private void reportHealth(Health newStatus, Severity severity, Map<String, Object> details) {
    if (!newStatus.equals(consumerStatus)) {
      consumerStatus = newStatus;
      context.log(activity ->
          activity
              .withSeverity(severity)
              .withTag(ActivityLogTag.CONSUMER)
              .withData(details)
              .withMessage(
                  "Kafka Consumer status changed to "
                      + newStatus.getStatus()
                      + ", deduplication ID: "
                      + context.getDefinition().deduplicationId())
              .andReportHealth(newStatus));
    }
  }

  private void reportDown(Throwable error) {
    var newStatus = Health.down(error);
    if (!newStatus.equals(consumerStatus)) {
      consumerStatus = newStatus;
      context.log(activity ->
          activity
              .withSeverity(Severity.ERROR)
              .withTag(ActivityLogTag.CONSUMER)
              .withMessage(
                  "Kafka Consumer status changed to DOWN, deduplication ID: "
                      + context.getDefinition().deduplicationId(),
                  error)
              .andReportHealth(newStatus));
    }
  }
}
