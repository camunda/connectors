/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.rabbitmq.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import io.camunda.connector.rabbitmq.supplier.GsonSupplier;
import java.util.Optional;

@OutboundConnector(
    name = "RABBITMQ",
    inputVariables = {"authentication", "routing", "message"},
    type = "io.camunda:rabbitmq:1")
public class RabbitMqFunction implements OutboundConnectorFunction {

  private final ConnectionFactorySupplier connectionFactorySupplier;
  private final Gson gson;

  public RabbitMqFunction() {
    this.connectionFactorySupplier = new ConnectionFactorySupplier();
    this.gson = new GsonSupplier().gson();
  }

  public RabbitMqFunction(
      final ConnectionFactorySupplier connectionFactorySupplier, final Gson gson) {
    this.connectionFactorySupplier = connectionFactorySupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    final var connectorRequest = context.getVariablesAsType(RabbitMqRequest.class);
    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);
    return executeConnector(connectorRequest);
  }

  private RabbitMqResult executeConnector(final RabbitMqRequest request) throws Exception {
    final var messageProperties =
        Optional.ofNullable(request.getMessage().getProperties())
            .map(gson::toJson)
            .map(jsonProperties -> gson.fromJson(jsonProperties, AMQP.BasicProperties.class))
            .orElse(null);

    final var messageInByteArray =
        Optional.of(request.getMessage().getBody())
            .map(gson::toJson)
            .map(String::getBytes)
            .orElseThrow(() -> new RuntimeException("Parse error to byte array"));

    try (Connection connection = connectionFactorySupplier.createFactory(request).newConnection()) {
      final var channel = connection.createChannel();
      channel.basicPublish(
          request.getRouting().getExchange(),
          request.getRouting().getRoutingKey(),
          messageProperties,
          messageInByteArray);
      return RabbitMqResult.success();
    }
  }
}
