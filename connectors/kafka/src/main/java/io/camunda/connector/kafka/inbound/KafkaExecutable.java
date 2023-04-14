/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "KAFKA", type = "io.camunda:connector-kafka-inbound:1")
public class KafkaExecutable implements InboundConnectorExecutable {

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);

  private CompletableFuture<?> future;

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    KafkaConnectorProperties props =
        connectorContext.getPropertiesAsType(KafkaConnectorProperties.class);
    connectorContext.replaceSecrets(props);
    connectorContext.validate(props);

    final Properties kafkaProps = getKafkaProperties(props);

    this.future =
        CompletableFuture.supplyAsync(
            () -> {
              KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);
              consumer.subscribe(
                  Arrays.asList(
                      props.getTopic().getTopicName())); // TODO : should we allow multiple topics?
              LOG.trace("Kafka inbound connector initialized");
              while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                  LOG.trace(
                      "Kafka message received: key = %s, value = %s%n",
                      record.key(), record.value());
                  connectorContext.correlate(convertConsumerRecordToKafkaInboundMessage(record));
                }
              }
            });
  }

  @Override
  public void deactivate() {
    this.future.cancel(true);
  }

  private Properties getKafkaProperties(KafkaConnectorProperties props) {
    KafkaConnectorRequest connectorRequest = new KafkaConnectorRequest();
    connectorRequest.setTopic(props.getTopic());
    connectorRequest.setAuthentication(props.getAuthentication());
    connectorRequest.setAdditionalProperties(props.getAdditionalProperties());
    final Properties kafkaProps = connectorRequest.assembleKafkaClientProperties();
    if (kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG) == null) {
      kafkaProps.put(
          ConsumerConfig.GROUP_ID_CONFIG,
          "kafka-inbound-connector"); // GROUP_ID_CONFIG is mandatory
    }
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
      kafkaInboundMessage.setValue(JsonParser.parseString(consumerRecord.value()));
    } catch (JsonSyntaxException e) {
      LOG.debug("Cannot parse value to json object -> use the raw value");
      kafkaInboundMessage.setValue(kafkaInboundMessage.getRawValue());
    }
    return kafkaInboundMessage;
  }
}
