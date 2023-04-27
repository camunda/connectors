/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.supplier.GsonSupplier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "KAFKA_INBOUND", type = "io.camunda:connector-kafka-inbound:1")
public class KafkaExecutable implements InboundConnectorExecutable {

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);

  static final String DEFAULT_GROUP_ID_PREFIX = "kafka-inbound-connector";

  Consumer<String, String> consumer;

  private final Function<Properties, Consumer<String, String>> consumerCreatorFunction;

  public CompletableFuture<?> future;

  private InboundConnectorContext context;

  boolean shouldLoop = true;

  public KafkaExecutable(
      final Function<Properties, Consumer<String, String>> consumerCreatorFunction) {
    this.consumerCreatorFunction = consumerCreatorFunction;
  }

  public KafkaExecutable() {
    this(KafkaConsumer::new);
  }

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    this.context = connectorContext;
    KafkaConnectorProperties elementProps =
        connectorContext.getPropertiesAsType(KafkaConnectorProperties.class);
    connectorContext.replaceSecrets(elementProps);
    connectorContext.validate(elementProps);
    this.consumer =
        createConsumer(
            this.consumerCreatorFunction,
            getKafkaProperties(elementProps, this.context),
            elementProps,
            getOffsets(elementProps.getOffsets()));
    LOG.debug("Kafka inbound connector initialized");
    this.future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                while (shouldLoop) {
                  ConsumerRecords<String, String> records =
                      this.consumer.poll(Duration.ofMillis(500));
                  for (ConsumerRecord<String, String> record : records) {
                    LOG.trace(
                        "Kafka message received: key = %s, value = %s%n",
                        record.key(), record.value());
                    InboundConnectorResult<?> result =
                        connectorContext.correlate(
                            convertConsumerRecordToKafkaInboundMessage(record));
                    if (result.isActivated()) {
                      LOG.debug(
                          "Inbound event correlated successfully: {}", result.getResponseData());
                    } else {
                      LOG.debug("Inbound event not correlated: {}", result.getErrorData());
                    }
                  }
                  this.consumer.commitSync();
                }
              } catch (ConnectorInputException e) {
                LOG.warn("Failed to parse message body: {}", e.getMessage());
                throw e;
              } catch (Exception ex) {
                LOG.error("Failed to execute connector: {}", ex.getMessage());
                throw ex;
              }
              LOG.debug("Kafka inbound loop finished");
              return null;
            });
  }

  @Override
  public void deactivate() {
    try {
      this.shouldLoop = false;
      if (this.future != null && !this.future.isDone()) {
        this.future.get();
      }
      this.shouldLoop = true;
    } catch (Exception e) {
      LOG.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
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
      if (partitions.size() < offsets.size()) {
        throw new ConnectorInputException(
            new IllegalArgumentException(
                "Number of offsets provided is greater then the number of partitions!"));
      } else if (partitions.size() > offsets.size()) {
        throw new ConnectorInputException(
            new IllegalArgumentException(
                "Number of offsets provided is less then the number of partitions!"));
      }
      for (int i = 0; i < offsets.size(); i++) {
        consumer.seek(topicPartitions.get(i), offsets.get(i));
      }
    }
    return consumer;
  }

  protected List<Long> getOffsets(Object offsets) {
    if (offsets == null) {
      return null;
    }
    List<Long> offsetCollection = null;
    if (offsets instanceof Collection<?>) {
      offsetCollection = (List<Long>) offsets;
    } else if (offsets instanceof String) {
      offsetCollection = convertStringToList((String) offsets);
    } else {
      // We accept only List or String input for offsets
      throw new IllegalArgumentException(
          "Invalid input type for offsets. Supported types are: List<Long> and String");
    }
    return offsetCollection;
  }

  private List<Long> convertStringToList(String string) {
    if (StringUtils.isBlank(string)) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(","))
        .map(s -> Long.parseLong(s.trim()))
        .collect(Collectors.toList());
  }

  protected Properties getKafkaProperties(
      KafkaConnectorProperties props, InboundConnectorContext context) {
    KafkaConnectorRequest connectorRequest = new KafkaConnectorRequest();
    connectorRequest.setTopic(props.getTopic());
    connectorRequest.setAuthentication(props.getAuthentication());
    connectorRequest.setAdditionalProperties(props.getAdditionalProperties());
    final Properties kafkaProps = connectorRequest.assembleKafkaClientProperties();
    if (kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG) == null) {
      kafkaProps.put(
          ConsumerConfig.GROUP_ID_CONFIG,
          DEFAULT_GROUP_ID_PREFIX
              + "-"
              + context
                  .getProperties()
                  .getCorrelationPointId()); // GROUP_ID_CONFIG is mandatory. It will be used to
      // assign a client id
    }
    kafkaProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getAutoOffsetReset().toString());
    kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    kafkaProps.put(TopicConfig.RETENTION_MS_CONFIG, -1);
    kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    return kafkaProps;
  }

  protected KafkaInboundMessage convertConsumerRecordToKafkaInboundMessage(
      ConsumerRecord<String, String> consumerRecord) {
    KafkaInboundMessage kafkaInboundMessage = new KafkaInboundMessage();
    kafkaInboundMessage.setKey(consumerRecord.key());
    kafkaInboundMessage.setRawValue(consumerRecord.value());
    try {
      JsonElement bodyAsJsonElement =
          GsonSupplier.gson()
              .fromJson(StringEscapeUtils.unescapeJson(consumerRecord.value()), JsonElement.class);
      kafkaInboundMessage.setValue(GsonSupplier.gson().fromJson(bodyAsJsonElement, Object.class));
    } catch (JsonSyntaxException e) {
      LOG.debug("Cannot parse value to json object -> use the raw value");
      kafkaInboundMessage.setValue(kafkaInboundMessage.getRawValue());
    }
    return kafkaInboundMessage;
  }
}
