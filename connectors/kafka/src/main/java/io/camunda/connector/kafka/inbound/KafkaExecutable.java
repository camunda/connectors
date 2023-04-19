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
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "KAFKA", type = "io.camunda:connector-kafka-inbound:1")
public class KafkaExecutable implements InboundConnectorExecutable {

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);

  static final String DEFAULT_GROUP_ID = "kafka-inbound-connector";

  KafkaConsumer<String, String> consumer;

  CompletableFuture<?> future;

  private InboundConnectorContext context;

  boolean shouldLoop = true;

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    this.context = connectorContext;
    KafkaConnectorProperties props =
        connectorContext.getPropertiesAsType(KafkaConnectorProperties.class);
    connectorContext.replaceSecrets(props);
    connectorContext.validate(props);

    final Properties kafkaProps = getKafkaProperties(props);

    this.future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                this.consumer = new KafkaConsumer<>(kafkaProps);
                this.consumer.subscribe(
                    Arrays.asList(
                        props
                            .getTopic()
                            .getTopicName())); // Subscribe to the given list of topics to get
                // dynamically assigned partitions.

                if (props.getOffset() != null) {
                  Set<TopicPartition> partitions = consumer.assignment();
                  partitions.forEach(partition -> consumer.seek(partition, props.getOffset()));
                }

                LOG.debug("Kafka inbound connector initialized");
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
                    this.consumer.commitSync();
                    if (result.isActivated()) {
                      LOG.debug(
                          "Inbound event correlated successfully: {}", result.getResponseData());
                    } else {
                      LOG.debug("Inbound event not correlated: {}", result.getErrorData());
                    }
                  }
                }
              } catch (ConnectorInputException e) {
                LOG.warn("Failed to parse message body: {}", e.getMessage());
              } catch (Exception ex) {
                LOG.error("Failed to execute connector: {}", ex.getMessage());
              }
              LOG.debug("Kafka inbound loop finished");
              return null;
            });
  }

  @Override
  public void deactivate() {
    try {
      this.shouldLoop = false;
      if (!this.future.isDone()) {
        this.future.get();
      }
      this.shouldLoop = true;
    } catch (Exception e) {
      LOG.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
  }

  private Properties getKafkaProperties(KafkaConnectorProperties props) {
    KafkaConnectorRequest connectorRequest = new KafkaConnectorRequest();
    connectorRequest.setTopic(props.getTopic());
    connectorRequest.setAuthentication(props.getAuthentication());
    connectorRequest.setAdditionalProperties(props.getAdditionalProperties());
    final Properties kafkaProps = connectorRequest.assembleKafkaClientProperties();
    if (kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG) == null) {
      kafkaProps.put(
          ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_GROUP_ID); // GROUP_ID_CONFIG is mandatory
    }
    kafkaProps.put(ConsumerConfig.AUTO_OFFSET_RESET_DOC, props.getAutoOffsetReset());
    kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    return kafkaProps;
  }

  public KafkaInboundMessage convertConsumerRecordToKafkaInboundMessage(
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
