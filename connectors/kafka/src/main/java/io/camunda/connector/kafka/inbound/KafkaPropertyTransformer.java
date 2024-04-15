/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.SerializationType;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaPropertyTransformer {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaPropertyTransformer.class);

  static final String DEFAULT_GROUP_ID_PREFIX = "kafka-inbound-connector";

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  protected static final String BYTE_ARRAY_DESERIALIZER =
      "org.apache.kafka.common.serialization.ByteArrayDeserializer";

  public static Properties getKafkaProperties(
      KafkaConnectorProperties props, InboundConnectorContext context) {
    KafkaConnectorRequest connectorRequest =
        new KafkaConnectorRequest(
            SerializationType.JSON,
            props.authentication(),
            props.topic(),
            null,
            null,
            null,
            props.additionalProperties() == null ? new HashMap<>() : props.additionalProperties());
    final Properties kafkaProps =
        KafkaPropertiesUtil.assembleKafkaClientProperties(connectorRequest);

    if (kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG) == null) {
      var groupIdConfig = resolveGroupId(props, context);
      // GROUP_ID_CONFIG is mandatory. It will be used to assign a client id
      kafkaProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
    }
    kafkaProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.autoOffsetReset().toString());
    kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    kafkaProps.put(TopicConfig.RETENTION_MS_CONFIG, -1);

    kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);

    if (props.avro() == null) {
      kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    } else {
      kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, BYTE_ARRAY_DESERIALIZER);
    }

    return kafkaProps;
  }

  private static String resolveGroupId(
      KafkaConnectorProperties kafkaConnectorProperties, InboundConnectorContext context) {
    var clientId = kafkaConnectorProperties.groupId();
    if (kafkaConnectorProperties.groupId() == null) {
      clientId = computeGroupId(context);
    }
    return clientId.substring(0, Math.min(clientId.length(), 250));
  }

  private static String computeGroupId(InboundConnectorContext context) {
    return DEFAULT_GROUP_ID_PREFIX + "-" + context.getDefinition().deduplicationId();
  }

  public static KafkaInboundMessage convertConsumerRecordToKafkaInboundMessage(
      ConsumerRecord<Object, Object> consumerRecord, ObjectReader objectReader) {
    KafkaInboundMessage kafkaInboundMessage = new KafkaInboundMessage();
    kafkaInboundMessage.setKey(parseKey(consumerRecord, objectReader));
    setValue(consumerRecord, objectReader, kafkaInboundMessage);
    setHeadersIfPresent(consumerRecord, kafkaInboundMessage);
    return kafkaInboundMessage;
  }

  private static void setHeadersIfPresent(
      ConsumerRecord<Object, Object> consumerRecord, KafkaInboundMessage kafkaInboundMessage) {
    if (consumerRecord.headers() != null) {
      var headerMap = new HashMap<String, Object>();
      for (Header header : consumerRecord.headers()) {
        headerMap.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
      }
      if (!headerMap.isEmpty()) {
        kafkaInboundMessage.setHeaders(headerMap);
      }
    }
  }

  private static Object parseKey(
      ConsumerRecord<Object, Object> consumerRecord, ObjectReader objectReader) {
    try {
      return objectReader.readTree((String) consumerRecord.key());
    } catch (Exception e) {
      LOG.debug("Cannot parse key to json object -> use as string");
      return StringEscapeUtils.unescapeJson((String) consumerRecord.key());
    }
  }

  private static void setValue(
      ConsumerRecord<Object, Object> consumerRecord,
      ObjectReader objectReader,
      KafkaInboundMessage kafkaInboundMessage) {
    try {
      if (consumerRecord.value() instanceof byte[]) {
        kafkaInboundMessage.setValue(objectReader.readTree((byte[]) consumerRecord.value()));
      } else {
        String value = (String) consumerRecord.value();
        kafkaInboundMessage.setRawValue(value);
        kafkaInboundMessage.setValue(objectReader.readTree(value));
      }
    } catch (Exception e) {
      LOG.error("Cannot parse value to json object -> use the raw value", e);
      kafkaInboundMessage.setValue(kafkaInboundMessage.getRawValue());
    }
  }
}
