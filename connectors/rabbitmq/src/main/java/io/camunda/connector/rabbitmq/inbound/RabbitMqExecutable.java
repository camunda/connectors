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
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "RabbitMQ Consumer", type = "io.camunda:connector-rabbitmq-inbound:1")
public class RabbitMqExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

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
    RabbitMqInboundProperties properties = context.bindProperties(RabbitMqInboundProperties.class);

    LOGGER.info("Subscription activation requested by the Connector runtime: {}", properties);

    context.log(
        Activity.level(Severity.INFO)
            .tag("Subscription activation")
            .message(
                "Subscription activation requested for queue name :" + properties.getQueueName()));

    initializeConsumer(context, properties);
  }

  @Override
  public void deactivate() throws Exception {
    LOGGER.info("Subscription deactivation requested by the Connector runtime");
    try {
      channel.basicCancel(consumerTag);
    } catch (Exception e) {
      LOGGER.warn("Failed to cancel consumer", e);
    } finally {
      if (connection != null) {
        connection.close(CLOSE_TIMEOUT_MILLIS);
      }
    }
  }

  void initializeConsumer(InboundConnectorContext context, RabbitMqInboundProperties properties)
      throws Exception {

    connection = openConnection(properties);

    if (connection instanceof Recoverable recoverable) {
      final var recoveryListener =
          new RecoveryListener() {
            @Override
            public void handleRecovery(Recoverable recoverable) {
              LOGGER.info("Connection recovered successfully: {}", recoverable);
              context.log(
                  Activity.level(Severity.INFO)
                      .tag("Connection recovery")
                      .message("Connection recovered successfully: " + recoverable));
              context.reportHealth(Health.up());
            }

            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
              LOGGER.info("Connection recovery started: {}", recoverable);
              context.log(
                  Activity.level(Severity.INFO)
                      .tag("Connection recovery")
                      .message("Connection recovery started: " + recoverable));
              context.reportHealth(Health.down());
            }
          };
      recoverable.addRecoveryListener(recoveryListener);
    }

    channel = connection.createChannel();
    Consumer consumer = new RabbitMqConsumer(channel, context);

    var data = new HashMap<String, Object>();
    data.put("connection-id", connection.getId());
    data.put("connection-name", connection.getClientProvidedName());
    data.put("connection-address", connection.getAddress());
    data.put("connection-port", connection.getPort());
    context.reportHealth(Health.up(data));

    consumerTag = startConsumer(properties, consumer);
    LOGGER.info("Started RabbitMQ consumer for queue {}", properties.getQueueName());
  }

  Connection openConnection(RabbitMqInboundProperties properties) throws Exception {
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
