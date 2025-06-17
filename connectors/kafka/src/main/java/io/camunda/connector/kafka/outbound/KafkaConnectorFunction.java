/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.kafka.outbound.model.ProducerRecordFactory;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
      "schemaStrategy",
      "additionalProperties",
      "headers"
    },
    type = "io.camunda:connector-kafka:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.KAFKA.v1",
    name = "Kafka Outbound Connector",
    description = "Produce Kafka message",
    inputDataClass = KafkaConnectorRequest.class,
    version = 7,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "kafka", label = "Kafka"),
      @ElementTemplate.PropertyGroup(id = "schema", label = "Schema"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=outbound",
    icon = "icon.svg")
public class KafkaConnectorFunction implements OutboundConnectorFunction {

  private final Function<Properties, Producer<String, Object>> producerCreatorFunction;

  private final ProducerRecordFactory producerRecordFactory = new ProducerRecordFactory();

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

  private KafkaConnectorResponse executeConnector(final KafkaConnectorRequest request) {
    Properties props = KafkaPropertiesUtil.assembleKafkaClientProperties(request);
    try (Producer<String, Object> producer = producerCreatorFunction.apply(props)) {
      ProducerRecord<String, Object> producerRecord =
          producerRecordFactory.createProducerRecord(request);
      Future<RecordMetadata> kafkaResponse = producer.send(producerRecord);
      return constructKafkaConnectorResponse(kafkaResponse.get(45, TimeUnit.SECONDS));
    } catch (Exception e) {
      throw new ConnectorException(
          "FAIL",
          "Error during Kafka Producer execution; error message: [" + e.getMessage() + "]",
          e);
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
