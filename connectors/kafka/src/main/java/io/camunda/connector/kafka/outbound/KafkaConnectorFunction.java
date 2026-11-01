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
import io.camunda.connector.kafka.model.KafkaConnectionConfiguration;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.kafka.outbound.model.ProducerRecordFactory;
import io.camunda.connector.kafka.outbound.model.RegistrySchemaResolver;
import io.camunda.connector.kafka.outbound.model.ResolvedSchema;
import java.util.Optional;
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
      "kafkaConnectionConfiguration",
      "authentication",
      "topic",
      "message",
      "schemaStrategy",
      "additionalProperties",
      "headers"
    },
    type = "io.camunda:connector-kafka:1")
@ElementTemplate(
    engineVersion = "^8.11",
    id = "io.camunda.connectors.KAFKA.v1",
    name = "Publish Message to Kafka",
    description = "Produce Kafka message",
    keywords = {
      "publish message",
      "send message",
      "message queue",
      "event streaming",
      "produce event",
      "stream data",
      "kafka topic"
    },
    inputDataClass = KafkaConnectorRequest.class,
    version = 8,
    configurations = {KafkaConnectionConfiguration.class},
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Connection"),
      @ElementTemplate.PropertyGroup(id = "kafka", label = "Kafka"),
      @ElementTemplate.PropertyGroup(id = "schema", label = "Schema"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message")
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/kafka/?kafka=outbound",
    icon = "icon.svg")
public class KafkaConnectorFunction implements OutboundConnectorFunction {

  private final Function<Properties, Producer<String, Object>> producerCreatorFunction;

  private final ProducerRecordFactory producerRecordFactory = new ProducerRecordFactory();
  private final RegistrySchemaResolver registrySchemaResolver;

  public KafkaConnectorFunction() {
    this(KafkaProducer::new);
  }

  public KafkaConnectorFunction(
      final Function<Properties, Producer<String, Object>> producerCreatorFunction) {
    this(producerCreatorFunction, new RegistrySchemaResolver());
  }

  public KafkaConnectorFunction(
      final Function<Properties, Producer<String, Object>> producerCreatorFunction,
      final RegistrySchemaResolver registrySchemaResolver) {
    this.producerCreatorFunction = producerCreatorFunction;
    this.registrySchemaResolver = registrySchemaResolver;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    var connectorRequest = context.bindVariables(KafkaConnectorRequest.class);
    return executeConnector(connectorRequest);
  }

  private KafkaConnectorResponse executeConnector(final KafkaConnectorRequest request) {
    var resolvedSchema = resolveRegisteredSchema(request);
    Properties props = KafkaPropertiesUtil.assembleKafkaClientProperties(request, resolvedSchema);
    try {
      return send(request, props, resolvedSchema);
    } catch (Exception e) {
      throw new ConnectorException(
          "FAIL",
          "Error during Kafka Producer execution; error message: [" + e.getMessage() + "]",
          e);
    }
  }

  private Optional<ResolvedSchema> resolveRegisteredSchema(final KafkaConnectorRequest request) {
    try {
      return registrySchemaResolver.resolve(request);
    } catch (Exception e) {
      throw new ConnectorException(
          "FAIL",
          "Could not fetch the schema from the registry; error message: [" + e.getMessage() + "]",
          e);
    }
  }

  private KafkaConnectorResponse send(
      final KafkaConnectorRequest request,
      final Properties props,
      final Optional<ResolvedSchema> resolvedSchema)
      throws Exception {
    try (Producer<String, Object> producer = producerCreatorFunction.apply(props)) {
      ProducerRecord<String, Object> producerRecord =
          producerRecordFactory.createProducerRecord(request, resolvedSchema);
      Future<RecordMetadata> kafkaResponse = producer.send(producerRecord);
      return constructKafkaConnectorResponse(kafkaResponse.get(45, TimeUnit.SECONDS));
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
