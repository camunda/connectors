/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage;
import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.getKafkaProperties;
import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.getOffsets;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

  public CompletableFuture<?> future;

  Consumer<String, String> consumer;

  boolean shouldLoop = true;

  private final KafkaConnectorProperties elementProps;

  private final Function<Properties, Consumer<String, String>> consumerCreatorFunction;

  public KafkaConnectorConsumer(
      final Function<Properties, Consumer<String, String>> consumerCreatorFunction,
      final InboundConnectorContext connectorContext,
      final KafkaConnectorProperties elementProps) {
    this.consumerCreatorFunction = consumerCreatorFunction;
    this.context = connectorContext;
    this.elementProps = elementProps;
  }

  public void startConsumer() {
    this.consumer =
        createConsumer(
            this.consumerCreatorFunction,
            getKafkaProperties(elementProps, this.context),
            elementProps,
            getOffsets(elementProps.getOffsets()));
    LOG.info("Kafka inbound connector initialized");
    this.future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                while (shouldLoop) {
                  ConsumerRecords<String, String> records =
                      this.consumer.poll(Duration.ofMillis(500));
                  for (ConsumerRecord<String, String> record : records) {
                    handleMessage(record);
                  }
                  if (!records.isEmpty()) {
                    this.consumer.commitSync();
                  }
                }
              } catch (Exception ex) {
                LOG.error("Failed to execute connector: {}", ex.getMessage());
                throw ex;
              }
              LOG.debug("Kafka inbound loop finished");
              return null;
            });
  }

  private void handleMessage(ConsumerRecord<String, String> record) {
    LOG.trace("Kafka message received: key = %s, value = %s%n", record.key(), record.value());
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
  }

  private Consumer<String, String> createConsumer(
      final Function<Properties, Consumer<String, String>> consumerCreatorFunction,
      final Properties kafkaProps,
      final KafkaConnectorProperties elementProps,
      final List<Long> offsets) {
    // init
    Consumer<String, String> consumer = consumerCreatorFunction.apply(kafkaProps);

    // dynamically assign partitions to be able to handle offsets
    List<PartitionInfo> partitions = consumer.partitionsFor(elementProps.getTopic().getTopicName());
    List<TopicPartition> topicPartitions =
        partitions.stream()
            .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
            .collect(Collectors.toList());
    consumer.assign(topicPartitions);

    // set partition offsets if necessary
    if (offsets != null) {
      if (partitions.size() != offsets.size()) {
        throw new ConnectorInputException(
            new IllegalArgumentException(
                "Number of offsets provided is not equal the number of partitions!"));
      }
      for (int i = 0; i < offsets.size(); i++) {
        consumer.seek(topicPartitions.get(i), offsets.get(i));
      }
    }
    return consumer;
  }
}
