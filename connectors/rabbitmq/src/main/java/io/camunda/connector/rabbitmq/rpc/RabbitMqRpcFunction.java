/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.rabbitmq.inbound.AMQPPropertyUtil;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.rpc.model.RabbitMqRpcResult;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "RabbitMQ RPC Producer",
    inputVariables = {"authentication", "routing", "message"},
    type = "io.camunda:connector-rabbitmq-rpc:1")
public class RabbitMqRpcFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqRpcFunction.class);

  private final ConnectionFactorySupplier connectionFactorySupplier;

  public RabbitMqRpcFunction() {
    this.connectionFactorySupplier = new ConnectionFactorySupplier();
  }

  public RabbitMqRpcFunction(final ConnectionFactorySupplier connectionFactorySupplier) {
    this.connectionFactorySupplier = connectionFactorySupplier;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(RabbitMqRequest.class);
    return executeConnector(request);
  }

  private RabbitMqRpcResult executeConnector(final RabbitMqRequest request) throws Exception {

    // Getting properties and body before open new connection, because methods can throw exception
    final var messageProperties = request.getMessage().getPropertiesAsAmqpBasicProperties();
    final var messageInByteArray = request.getMessage().getBodyAsByteArray();
    final var routingKey = request.getRouting().getRoutingKey();

    try (Connection connection = openConnection(request)) {
      final var channel = connection.createChannel();

      String replyQueueName = channel.queueDeclare().getQueue();

      RpcClientParams rpcClientParams =
          new RpcClientParams()
              .channel(channel)
              .exchange(request.getRouting().getExchange())
              .routingKey(routingKey)
              .replyTo(replyQueueName)
              .correlationIdSupplier(() -> UUID.randomUUID().toString())
              .timeout(10000); // TODO: 10 seconds

      RpcClient rpcClient = new RpcClient(rpcClientParams);

      try {
        LOGGER.debug(
            "Sending RPC message to {} with replyQueueName {}", routingKey, replyQueueName);
        RpcClient.Response response = rpcClient.doCall(messageProperties, messageInByteArray);

        LOGGER.debug(
            "Got response for RPC message to {} with replyQueueName {}",
            routingKey,
            replyQueueName);
        return prepareVariables(
            response.getConsumerTag(), response.getProperties(), response.getBody());
      } catch (java.util.concurrent.TimeoutException e) {
        throw new ConnectorException("Timeout while waiting for RPC response");
      }
    }
  }

  private Connection openConnection(RabbitMqRequest request) throws Exception {
    return connectionFactorySupplier
        .createFactory(request.getAuthentication(), request.getRouting())
        .newConnection();
  }

  private RabbitMqRpcResult prepareVariables(
      String consumerTag, AMQP.BasicProperties rawProperties, byte[] body) {

    try {
      String bodyAsString = new String(body, StandardCharsets.UTF_8);
      Object bodyAsObject;
      if (isPayloadJson(bodyAsString)) {
        JsonNode bodyAsJsonElement =
            ObjectMapperSupplier.instance()
                .readValue(StringEscapeUtils.unescapeJson(bodyAsString), JsonNode.class);
        if (bodyAsJsonElement instanceof ValueNode bodyAsPrimitive) {
          if (bodyAsPrimitive.isBoolean()) {
            bodyAsObject = bodyAsPrimitive.asBoolean();
          } else if (bodyAsPrimitive.isNumber()) {
            bodyAsObject = bodyAsPrimitive.asDouble();
          } else {
            bodyAsObject = bodyAsPrimitive.asText();
          }
        } else {
          bodyAsObject =
              ObjectMapperSupplier.instance().convertValue(bodyAsJsonElement, Object.class);
        }
      } else {
        bodyAsObject = bodyAsString;
      }
      RabbitMqRpcResult.RabbitMqRpcMessage message =
          new RabbitMqRpcResult.RabbitMqRpcMessage(
              consumerTag, bodyAsObject, AMQPPropertyUtil.toProperties(rawProperties));
      return new RabbitMqRpcResult(message);

    } catch (Exception e) {
      LOGGER.error("Failed to parse AMQP message body: {}", e.getMessage());
      throw new ConnectorInputException(e);
    }
  }

  private boolean isPayloadJson(final String bodyAsString) {
    try {
      ObjectMapperSupplier.instance()
          .readValue(StringEscapeUtils.unescapeJson(bodyAsString), JsonNode.class);
    } catch (JsonProcessingException e) {
      return false;
    }
    return true;
  }
}
