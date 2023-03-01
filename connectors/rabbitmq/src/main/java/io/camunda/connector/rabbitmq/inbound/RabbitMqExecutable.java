/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "RABBITMQ", type = "io.camunda:connector-rabbitmq-inbound:1")
public class RabbitMqExecutable implements InboundConnectorExecutable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqExecutable.class);
  private static final int CLOSE_TIMEOUT_MILLIS = 3000;

  private Connection connection;
  private Channel channel;
  private String consumerTag; // either provided in properties or generated by RabbitMQ server

  private final ConnectionFactorySupplier connectionFactorySupplier;

  public RabbitMqExecutable() {
    this.connectionFactorySupplier = new ConnectionFactorySupplier();
  }

  public RabbitMqExecutable(final ConnectionFactorySupplier connectionFactorySupplier) {
    this.connectionFactorySupplier = connectionFactorySupplier;
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    RabbitMqInboundProperties properties =
        context.getPropertiesAsType(RabbitMqInboundProperties.class);

    LOGGER.info("Subscription activation requested by the Connector runtime: {}", properties);
    context.replaceSecrets(properties);
    context.validate(properties);

    connection = openConnection(properties);
    channel = connection.createChannel();
    Consumer consumer = new RabbitMqConsumer(channel, context);

    consumerTag = startConsumer(properties, consumer);
    LOGGER.info("Started RabbitMQ consumer for queue {}", properties.getQueueName());
  }

  @Override
  public void deactivate() throws Exception {
    LOGGER.info("Subscription deactivation requested by the Connector runtime");
    try {
      channel.basicCancel(consumerTag);
    } catch (Exception e) {
      LOGGER.warn("Failed to cancel consumer", e);
    } finally {
      connection.close(CLOSE_TIMEOUT_MILLIS);
    }
  }

  private Connection openConnection(RabbitMqInboundProperties properties) throws Exception {
    return connectionFactorySupplier
        .createFactory(properties.getAuthentication(), properties.getRouting())
        .newConnection();
  }

  private String startConsumer(RabbitMqInboundProperties properties, Consumer consumer)
      throws IOException {

    if (StringUtils.isBlank(properties.getConsumerTag())) {
      // generate a random consumer tag if it wasn't provided
      return channel.basicConsume(
          properties.getQueueName(),
          false,
          UUID.randomUUID().toString(),
          false,
          properties.isExclusive(),
          properties.getArguments(),
          consumer);
    }

    return channel.basicConsume(
        properties.getQueueName(),
        false,
        properties.getConsumerTag(),
        false,
        properties.isExclusive(),
        properties.getArguments(),
        consumer);
  }
}
