/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

@OutboundConnector(
    name = "RabbitMQ Producer",
    inputVariables = {"authentication", "routing", "message"},
    type = "io.camunda:connector-rabbitmq:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.RabbitMQ.v1",
    name = "Publish Message to RabbitMQ",
    description = "Send message to RabbitMQ",
    keywords = {
      "publish message",
      "send message",
      "message queue",
      "AMQP",
      "produce event",
      "message broker",
      "queue"
    },
    inputDataClass = RabbitMqRequest.class,
    version = 6,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "routing", label = "Routing"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound",
    icon = "icon.svg")
public class RabbitMqFunction implements OutboundConnectorFunction {

  static final String PUBLISH_FAILED_ERROR_CODE = "RABBITMQ_PUBLISH_FAILED";
  private static final long PUBLISH_CONFIRM_TIMEOUT_MILLIS = 30_000;

  private final ConnectionFactorySupplier connectionFactorySupplier;

  public RabbitMqFunction() {
    this.connectionFactorySupplier = new ConnectionFactorySupplier();
  }

  public RabbitMqFunction(final ConnectionFactorySupplier connectionFactorySupplier) {
    this.connectionFactorySupplier = connectionFactorySupplier;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(RabbitMqRequest.class);
    return executeConnector(request);
  }

  private RabbitMqResult executeConnector(final RabbitMqRequest request) throws Exception {

    // Getting properties and body before open new connection, because methods can throw exception
    final var messageProperties = MessageUtil.toAmqpBasicProperties(request.message().properties());
    final var messageInByteArray = MessageUtil.getBodyAsByteArray(request.message().body());

    try (Connection connection = openConnection(request)) {
      final var channel = connection.createChannel();
      channel.confirmSelect();
      try {
        channel.basicPublish(
            request.routing().exchange(),
            request.routing().routingKey(),
            messageProperties,
            messageInByteArray);
        channel.waitForConfirmsOrDie(PUBLISH_CONFIRM_TIMEOUT_MILLIS);
      } catch (ShutdownSignalException e) {
        throw new ConnectorException(
            PUBLISH_FAILED_ERROR_CODE, "RabbitMQ rejected the message: " + describeShutdown(e), e);
      } catch (IOException e) {
        throw new ConnectorException(
            PUBLISH_FAILED_ERROR_CODE, "RabbitMQ did not accept the message: " + e.getMessage(), e);
      } catch (TimeoutException e) {
        throw new ConnectorException(
            PUBLISH_FAILED_ERROR_CODE,
            "RabbitMQ did not confirm the message within " + PUBLISH_CONFIRM_TIMEOUT_MILLIS + " ms",
            e);
      }
      return RabbitMqResult.success();
    }
  }

  private static String describeShutdown(ShutdownSignalException e) {
    if (e.getReason() instanceof AMQP.Channel.Close close) {
      return close.getReplyText();
    }
    if (e.getReason() instanceof AMQP.Connection.Close close) {
      return close.getReplyText();
    }
    return e.getMessage();
  }

  private Connection openConnection(RabbitMqRequest request) throws Exception {
    return connectionFactorySupplier
        .createFactory(request.authentication(), request.routing().routingData())
        .newConnection();
  }
}
