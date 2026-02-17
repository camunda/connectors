/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Kafka Consumer", type = "io.camunda:connector-kafka-inbound:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.kafka",
    name = "Kafka Event Connector",
    icon = "icon.svg",
    version = 7,
    inputDataClass = KafkaConnectorProperties.class,
    description = "Consume Kafka messages",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=inbound",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "kafka", label = "Kafka"),
      @ElementTemplate.PropertyGroup(id = "schema", label = "Schema")
    },
    elementTypes = {
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
          templateNameOverride = "Kafka Boundary Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.inbound.KafkaReceive.v1",
          templateNameOverride = "Kafka Receive Task Connector")
    })
public class KafkaExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);
  private static final int INFINITE_RETRIES = -1;
  private final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction;
  private final RetryPolicy<Object> retryPolicy;
  public KafkaConnectorConsumer kafkaConnectorConsumer;

  public KafkaExecutable(
      final Function<Properties, Consumer<Object, Object>> consumerCreatorFunction,
      final RetryPolicy<Object> retryConfig) {
    this.consumerCreatorFunction = consumerCreatorFunction;
    this.retryPolicy = retryConfig;
  }

  public KafkaExecutable() {
    this(
        KafkaConsumer::new,
        RetryPolicy.builder()
            .handle(Exception.class)
            .withDelay(Duration.ofSeconds(30))
            .withMaxAttempts(INFINITE_RETRIES)
            .build());
  }

  @Override
  public void activate(InboundConnectorContext context) {
    try {
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.INFO)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage(
                      "Subscription activation for process "
                          + context.getDefinition().elements().stream()
                              .map(ProcessElement::bpmnProcessId)
                              .toList()));

      KafkaConnectorProperties elementProps =
          context.bindProperties(KafkaConnectorProperties.class);
      this.kafkaConnectorConsumer =
          new KafkaConnectorConsumer(consumerCreatorFunction, context, elementProps, retryPolicy);
      this.kafkaConnectorConsumer.startConsumer();
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.INFO)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage("Subscription activated successfully"));
    } catch (Exception ex) {
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage("Subscription activation failed: " + ex.getMessage()));
      context.reportHealth(Health.down(ex));
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
