/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import com.rabbitmq.client.Connection;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;

@OutboundConnector(
    name = "RabbitMQ Producer/Manager",
    inputVariables = {"authentication", "routing", "message", "operationType"},
    type = "io.camunda:connector-rabbitmq:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.RabbitMQ.v2",
    name = "RabbitMQ Outbound Connector Manager",
    description = "Send message and delete queue in RabbitMQ",
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

  private Object executeConnector(final RabbitMqRequest request) throws Exception {
    switch (request.operationType()) {
      case "sendMessage":
        if (request.message().body() == null) {
          throw new IllegalArgumentException(
              "Routing and Message data must be provided for 'sendMessage' operation.");
        }
        return sendMessage(request);

      case "deleteQueue":
        if (request.routing().routingKey() == null) {
          throw new IllegalArgumentException(
              "Queue name must be provided for 'deleteQueue' operation.");
        }
        return deleteQueue(request);

      default:
        throw new UnsupportedOperationException(
            "Operation type '" + request.operationType() + "' is not supported.");
    }
  }

  private RabbitMqResult sendMessage(final RabbitMqRequest request) throws Exception {
    // Getting properties and body before open new connection, because methods can throw exception
    final var messageProperties = MessageUtil.toAmqpBasicProperties(request.message().properties());
    final var messageInByteArray = MessageUtil.getBodyAsByteArray(request.message().body());

    try (Connection connection = openConnection(request)) {
      final var channel = connection.createChannel();
      channel.basicPublish(
          request.routing().exchange(),
          request.routing().routingKey(),
          messageProperties,
          messageInByteArray);
      return RabbitMqResult.success();
    }
  }

  private RabbitMqDeleteResult deleteQueue(final RabbitMqRequest request) throws Exception {

    final String queueName = request.routing().routingKey().trim();

    try (Connection connection = openConnection(request)) {
      final var channel = connection.createChannel();
      var deleteOk = channel.queueDelete(queueName);
      boolean deleted = deleteOk != null;

      if (deleted) {
        return RabbitMqDeleteResult.success();
      } else {
        return RabbitMqDeleteResult.failure("delete-returned-null");
      }
    } catch (Exception e) {
      return RabbitMqDeleteResult.failure(e.getMessage());
    }
  }

  private Connection openConnection(RabbitMqRequest request) throws Exception {
    return connectionFactorySupplier
        .createFactory(request.authentication(), request.routing().routingData())
        .newConnection();
  }
}
