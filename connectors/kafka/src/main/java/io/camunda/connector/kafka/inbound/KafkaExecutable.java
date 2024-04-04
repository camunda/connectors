/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Kafka Consumer", type = "io.camunda:connector-kafka-inbound:1")
@ElementTemplate(
    id = "io.camunda.connectors.webhook",
    name = "Kafka Event Connector",
    icon = "icon.svg",
    version = 5,
    inputDataClass = KafkaConnectorProperties.class,
    description = "Consume Kafka messages",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=inbound",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "kafka", label = "Kafka"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message deserialization"),
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.KAFKA.v1",
          templateNameOverride = "Kafka Start Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.KafkaMessageStart.v1",
          templateNameOverride = "Kafka Message Start Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.KafkaIntermediate.v1",
          templateNameOverride = "Kafka Intermediate Catch Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.KafkaBoundary.v1",
          templateNameOverride = "Kafka Boundary Event Connector")
    })
public class KafkaExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);
  private final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction;
  public KafkaConnectorConsumer kafkaConnectorConsumer;

  public KafkaExecutable(
      final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction) {
    this.consumerCreatorFunction = consumerCreatorFunction;
  }

  public KafkaExecutable() {
    this(KafkaConsumer::new);
  }

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    try {
      KafkaConnectorProperties elementProps =
          connectorContext.bindProperties(KafkaConnectorProperties.class);
      this.kafkaConnectorConsumer =
          new KafkaConnectorConsumer(consumerCreatorFunction, connectorContext, elementProps);
      this.kafkaConnectorConsumer.startConsumer();
    } catch (Exception ex) {
      connectorContext.reportHealth(Health.down(ex));
      throw ex;
    }
  }

  @Override
  public void deactivate() {
    LOG.info("Subscription deactivation requested by the Connector runtime");
    try {
      this.kafkaConnectorConsumer.stopConsumer();
    } catch (Exception e) {
      LOG.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
  }
}
