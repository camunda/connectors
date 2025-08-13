/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.outbound.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.converter.GenericRecordConverter;
import io.camunda.connector.kafka.converter.ObjectNodeConverter;
import io.camunda.connector.kafka.model.schema.AvroInlineSchemaStrategy;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ProducerRecordFactory {

  private static final ObjectNodeConverter OBJECT_NODE_CONVERTER = new ObjectNodeConverter();
  private static final GenericRecordConverter GENERIC_RECORD_CONVERTER =
      new GenericRecordConverter();
  private static final ObjectMapper OBJECT_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy().enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

  public ProducerRecord<String, Object> createProducerRecord(final KafkaConnectorRequest request)
      throws Exception {
    Object transformedValue = createMessage(request);
    String transformedKey = transformData(request.message().key());
    Map<String, String> headers = Optional.ofNullable(request.headers()).orElse(new HashMap<>());
    var record =
        new ProducerRecord<>(
            request.topic().topicName(), null, null, transformedKey, transformedValue);
    addHeadersToProducerRecord(record, headers);

    return record;
  }

  private Object createMessage(final KafkaConnectorRequest request) throws Exception {
    var value = request.message().value();
    var strategy = request.schemaStrategy();
    return switch (strategy) {
      case AvroInlineSchemaStrategy s -> produceAvroMessage(s, value);
      case OutboundSchemaRegistryStrategy s -> produceSchemaRegistryMessage(s, value);
      default -> transformData(request.message().value());
    };
  }

  private String transformData(Object data) throws JsonProcessingException {
    return data instanceof String ? (String) data : OBJECT_MAPPER.writeValueAsString(data);
  }

  private byte[] produceAvroMessage(AvroInlineSchemaStrategy strategy, Object messageValue)
      throws Exception {
    var schemaString = strategy.schema();
    Schema raw = new Schema.Parser().parse(schemaString);
    AvroSchema schema = new AvroSchema(raw);
    AvroMapper avroMapper = new AvroMapper();
    if (messageValue instanceof String messageValueAsString) {
      messageValue = OBJECT_MAPPER.readTree(messageValueAsString);
    }
    return avroMapper.writer(schema).writeValueAsBytes(messageValue);
  }

  private Object produceSchemaRegistryMessage(
      OutboundSchemaRegistryStrategy strategy, Object messageValue) throws JsonProcessingException {
    if (messageValue instanceof String messageValueAsString) {
      messageValue = OBJECT_MAPPER.readValue(messageValueAsString, Map.class);
    }
    if (!(messageValue instanceof Map)) {
      throw new ConnectorException(
          "FAIL", "Message value must be a map for a schema based message");
    }

    var schemaString = strategy.getSchema();
    return switch (strategy.getSchemaType()) {
      case AVRO ->
          GENERIC_RECORD_CONVERTER.toGenericRecord(
              new Schema.Parser().parse(schemaString), (Map) messageValue);
      case JSON -> OBJECT_NODE_CONVERTER.toObjectNode(schemaString, (Map) messageValue);
    };
  }

  private void addHeadersToProducerRecord(
      ProducerRecord<String, Object> producerRecord, Map<String, String> headers) {
    headers.forEach((k, v) -> producerRecord.headers().add(k, v.getBytes()));
  }
}
