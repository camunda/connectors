/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.converter.GenericRecordConverter;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.schema.AvroInlineSchemaStrategy;
import io.camunda.connector.kafka.model.schema.InboundSchemaRegistryStrategy;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaPropertyTransformer {

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";
  protected static final String BYTE_ARRAY_DESERIALIZER =
      "org.apache.kafka.common.serialization.ByteArrayDeserializer";
  static final String DEFAULT_GROUP_ID_PREFIX = "kafka-inbound-connector";
  private static final Logger LOG = LoggerFactory.getLogger(KafkaPropertyTransformer.class);

  private static final GenericRecordConverter GENERIC_RECORD_CONVERTER =
      new GenericRecordConverter();

  public static Properties getKafkaProperties(
      KafkaConnectorProperties props, InboundConnectorContext context) {
    KafkaConnectorRequest connectorRequest =
        new KafkaConnectorRequest(
            props.authentication(),
            props.topic(),
            null,
            new NoSchemaStrategy(),
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
    switch (props.schemaStrategy()) {
      case AvroInlineSchemaStrategy ignored:
        kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, BYTE_ARRAY_DESERIALIZER);
        break;
      case InboundSchemaRegistryStrategy strategy:
        kafkaProps.put("schema.registry.url", strategy.getSchemaRegistryUrl());
        var serializer =
            switch (strategy.getSchemaType()) {
              case AVRO -> KafkaAvroDeserializer.class;
              case JSON -> KafkaJsonSchemaDeserializer.class;
            };
        kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, serializer);
        kafkaProps.put(KafkaJsonSchemaDeserializerConfig.JSON_VALUE_TYPE, JsonNode.class);
        break;
      default:
        kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
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
        headerMap.put(
            header.key(),
            header.value() != null ? new String(header.value(), StandardCharsets.UTF_8) : null);
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
      return consumerRecord.key();
    }
  }

  private static void setValue(
      ConsumerRecord<Object, Object> consumerRecord,
      ObjectReader objectReader,
      KafkaInboundMessage kafkaInboundMessage) {
    try {
      var value =
          switch (consumerRecord.value()) {
            case byte[] bytes -> objectReader.readTree(bytes);
            case GenericRecord record -> GENERIC_RECORD_CONVERTER.toObjectNode(record);
            case JsonNode jsonNode -> {
              kafkaInboundMessage.setRawValue(
                  ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(jsonNode));
              yield jsonNode;
            }
            case String string -> {
              kafkaInboundMessage.setRawValue(string);
              yield objectReader.readTree(string);
            }
            default -> consumerRecord.value();
          };
      kafkaInboundMessage.setValue(value);
    } catch (Exception e) {
      LOG.error("Cannot parse value to json object -> use the raw value", e);
      kafkaInboundMessage.setValue(kafkaInboundMessage.getRawValue());
    }
  }
}
