/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage;
import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.getKafkaProperties;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnectorConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaConnectorConsumer.class);

  private final InboundConnectorContext context;

  private ExecutorService executorService;

  public CompletableFuture<?> future;

  Consumer<String, String> consumer;

  KafkaConnectorProperties elementProps;

  boolean shouldLoop = true;

  private final Function<Properties, Consumer<String, String>> consumerCreatorFunction;

  public KafkaConnectorConsumer(
      final Function<Properties, Consumer<String, String>> consumerCreatorFunction,
      final InboundConnectorContext connectorContext,
      final KafkaConnectorProperties elementProps) {
    this.consumerCreatorFunction = consumerCreatorFunction;
    this.context = connectorContext;
    this.elementProps = elementProps;
    this.executorService = Executors.newSingleThreadExecutor();
  }

  public void startConsumer() {
    this.future =
        CompletableFuture.runAsync(
            () -> {
              prepareConsumer();
              consume();
            },
            this.executorService);
  }

  private void prepareConsumer() {
    try {
      this.consumer = consumerCreatorFunction.apply(getKafkaProperties(elementProps, context));
      var partitions = assignTopicPartitions(consumer, elementProps.getTopic().getTopicName());
      Optional.ofNullable(elementProps.getOffsets())
          .ifPresent(offsets -> seekOffsets(consumer, partitions, offsets));
      reportUp();
    } catch (Exception ex) {
      LOG.error("Failed to initialize connector: {}", ex.getMessage());
      context.reportHealth(Health.down(ex));
      throw ex;
    }
  }

  private List<TopicPartition> assignTopicPartitions(
      Consumer<String, String> consumer, String topic) {
    // dynamically assign partitions to be able to handle offsets
    List<PartitionInfo> partitions = consumer.partitionsFor(topic);
    List<TopicPartition> topicPartitions =
        partitions.stream()
            .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
            .collect(Collectors.toList());
    consumer.assign(topicPartitions);
    return topicPartitions;
  }

  private void seekOffsets(
      Consumer<String, String> consumer, List<TopicPartition> partitions, List<Long> offsets) {
    if (partitions.size() != offsets.size()) {
      throw new ConnectorInputException(
          new IllegalArgumentException(
              "Number of offsets provided is not equal the number of partitions!"));
    }
    for (int i = 0; i < offsets.size(); i++) {
      consumer.seek(partitions.get(i), offsets.get(i));
    }
    LOG.info("Kafka inbound connector initialized");
  }

  private void consume() {
    // TODO add resilient4j we should wait in case of any exceptions and only go down
    // after X amount of attempts
    while (shouldLoop) {
      try {
        ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
          handleMessage(record);
        }
        if (!records.isEmpty()) {
          this.consumer.commitSync();
        }
        reportUp();
      } catch (Exception ex) {
        LOG.error("Failed to execute connector: {}", ex.getMessage());
        context.reportHealth(Health.down(ex));
      }
    }
    LOG.debug("Kafka inbound loop finished");
  }

  private void handleMessage(ConsumerRecord<String, String> record) {
    LOG.trace("Kafka message received: key = {}, value = {}", record.key(), record.value());
    InboundConnectorResult<?> result =
        this.context.correlate(convertConsumerRecordToKafkaInboundMessage(record));
    if (result.isActivated()) {
      LOG.debug("Inbound event correlated successfully: {}", result.getResponseData());
    } else {
      LOG.debug("Inbound event not correlated: {}", result.getErrorData());
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
    context.reportHealth(Health.up(details));
  }
}
