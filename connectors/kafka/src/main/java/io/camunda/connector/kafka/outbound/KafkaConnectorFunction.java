/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

@OutboundConnector(
    name = "Kafka Producer",
    inputVariables = {
      "authentication",
      "topic",
      "message",
      "additionalProperties",
      "headers",
      "avro"
    },
    type = "io.camunda:connector-kafka:1")
public class KafkaConnectorFunction implements OutboundConnectorFunction {

  private final Function<Properties, Producer<String, Object>> producerCreatorFunction;

  private static final ObjectMapper objectMapper =
      ConnectorsObjectMapperSupplier.getCopy().enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

  public KafkaConnectorFunction() {
    this(KafkaProducer::new);
  }

  public KafkaConnectorFunction(
      final Function<Properties, Producer<String, Object>> producerCreatorFunction) {
    this.producerCreatorFunction = producerCreatorFunction;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    var connectorRequest = context.bindVariables(KafkaConnectorRequest.class);
    return executeConnector(connectorRequest);
  }

  public static byte[] produceAvroMessage(final KafkaConnectorRequest request) throws Exception {
    var schemaString = StringEscapeUtils.unescapeJson(request.avro().schema());
    Schema raw = new Schema.Parser().setValidate(true).parse(schemaString);
    AvroSchema schema = new AvroSchema(raw);
    AvroMapper avroMapper = new AvroMapper();
    Object messageValue = request.message().value();
    if (messageValue instanceof String messageValueAsString) {
      messageValue = objectMapper.readTree(StringEscapeUtils.unescapeJson(messageValueAsString));
    }
    return avroMapper.writer(schema).writeValueAsBytes(messageValue);
  }

  private KafkaConnectorResponse executeConnector(final KafkaConnectorRequest request) {
    Properties props = KafkaPropertiesUtil.assembleKafkaClientProperties(request);
    try (Producer<String, Object> producer = producerCreatorFunction.apply(props)) {
      Map<String, String> headers =
          (request.headers() != null) ? request.headers() : new HashMap<>();
      ProducerRecord<String, Object> producerRecord = createProducerRecord(request);
      addHeadersToProducerRecord(producerRecord, headers);
      Future<RecordMetadata> kafkaResponse = producer.send(producerRecord);
      return constructKafkaConnectorResponse(kafkaResponse.get(45, TimeUnit.SECONDS));
    } catch (Exception e) {
      throw new ConnectorException("FAIL", "Kafka Producer execution exception", e);
    }
  }

  private ProducerRecord<String, Object> createProducerRecord(final KafkaConnectorRequest request)
      throws Exception {
    Object transformedValue;
    if (request.avro() != null) {
      transformedValue = produceAvroMessage(request);
    } else {
      transformedValue = transformData(request.message().value());
    }
    String transformedKey = transformData(request.message().key());
    return new ProducerRecord<>(
        request.topic().topicName(), null, null, transformedKey, transformedValue);
  }

  public static String transformData(Object data) throws JsonProcessingException {
    return data instanceof String ? (String) data : objectMapper.writeValueAsString(data);
  }

  private void addHeadersToProducerRecord(
      ProducerRecord<String, Object> producerRecord, Map<String, String> headers) {
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        producerRecord
            .headers()
            .add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private KafkaConnectorResponse constructKafkaConnectorResponse(RecordMetadata recordMetadata) {
    return new KafkaConnectorResponse(
        recordMetadata.topic(),
        recordMetadata.timestamp(),
        recordMetadata.offset(),
        recordMetadata.partition());
  }
}
