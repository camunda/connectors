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
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;

@OutboundConnector(
    name = "RABBITMQ_OUTBOUND",
    inputVariables = {"authentication", "routing", "message"},
    type = "io.camunda:connector-rabbitmq:1")
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

  private RabbitMqResult executeConnector(final RabbitMqRequest request) throws Exception {

    // Getting properties and body before open new connection, because methods can throw exception
    final var messageProperties = request.getMessage().getPropertiesAsAmqpBasicProperties();
    final var messageInByteArray = request.getMessage().getBodyAsByteArray();

    try (Connection connection = openConnection(request)) {
      final var channel = connection.createChannel();
      channel.basicPublish(
          request.getRouting().getExchange(),
          request.getRouting().getRoutingKey(),
          messageProperties,
          messageInByteArray);
      return RabbitMqResult.success();
    }
  }

  private Connection openConnection(RabbitMqRequest request) throws Exception {
    return connectionFactorySupplier
        .createFactory(request.getAuthentication(), request.getRouting())
        .newConnection();
  }
}
