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
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "KAFKA_INBOUND", type = "io.camunda:connector-kafka-inbound:1")
public class KafkaExecutable implements InboundConnectorExecutable {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaExecutable.class);

  private final Function<Properties, Consumer<String, String>> consumerCreatorFunction;

  public KafkaConnectorConsumer kafkaConnectorConsumer;

  public KafkaExecutable(
      final Function<Properties, Consumer<String, String>> consumerCreatorFunction) {
    this.consumerCreatorFunction = consumerCreatorFunction;
  }

  public KafkaExecutable() {
    this(KafkaConsumer::new);
  }

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    try {
      KafkaConnectorProperties elementProps =
          connectorContext.getPropertiesAsType(KafkaConnectorProperties.class);
      LOG.info("Subscription activation requested by the Connector runtime: {}", elementProps);
      connectorContext.replaceSecrets(elementProps);
      connectorContext.validate(elementProps);
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
