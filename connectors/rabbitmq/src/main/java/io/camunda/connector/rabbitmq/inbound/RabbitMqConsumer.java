/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMqConsumer extends DefaultConsumer {

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqConsumer.class);

  private final InboundConnectorContext context;

  public RabbitMqConsumer(Channel channel, InboundConnectorContext context) {
    super(channel);
    this.context = context;
  }

  @Override
  public void handleDelivery(
      String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
      throws IOException {

    LOGGER.debug("Received AMQP message with delivery tag {}", envelope.getDeliveryTag());
    context.log(
        Activity.level(Severity.INFO)
            .tag("Message")
            .message("Received AMQP message with delivery tag " + envelope.getDeliveryTag()));

    try {
      RabbitMqInboundResult variables = prepareVariables(consumerTag, properties, body);
      var result = context.correlateWithResult(variables);
      handleCorrelationResult(envelope, result);
    } catch (Exception e) {
      LOGGER.debug("NACK (requeue) - unhandled exception", e);
      context.log(
          Activity.level(Severity.WARNING)
              .tag("Message")
              .message("NACK (requeue) - failed to correlate event"));
      getChannel().basicReject(envelope.getDeliveryTag(), true);
    }
  }

  private void handleCorrelationResult(Envelope envelope, CorrelationResult result)
      throws IOException {

    switch (result) {
      case Success ignored -> {
        LOGGER.debug("ACK - message correlated successfully");
        getChannel().basicAck(envelope.getDeliveryTag(), false);
      }

      case Failure failure -> {
        context.log(
            Activity.level(Severity.WARNING)
                .tag("Message")
                .message(
                    "Failed to handle AMQP message with delivery tag "
                        + envelope.getDeliveryTag()
                        + ", reason: "
                        + failure.message()));
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream fwdStrategy -> {
            if (fwdStrategy.isRetryable()) {
              LOGGER.debug("NACK (requeue) - message not correlated");
              getChannel().basicReject(envelope.getDeliveryTag(), true);
            } else {
              LOGGER.debug("NACK (drop) - message not correlated");
              getChannel().basicReject(envelope.getDeliveryTag(), false);
            }
          }
          case Ignore ignored -> {
            LOGGER.debug("ACK - message ignored");
            getChannel().basicAck(envelope.getDeliveryTag(), false);
          }
        }
      }
    }
  }

  @Override
  public void handleCancel(String consumerTag) {
    LOGGER.info("Consumer cancelled: {}", consumerTag);
    try {
      context.log(
          Activity.level(Severity.WARNING)
              .tag("Subscription")
              .message("Consumer cancelled: " + consumerTag));
      context.cancel(null);
    } catch (Exception e) {
      context.reportHealth(Health.down(e));
      LOGGER.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    LOGGER.error("Consumer shutdown: {}", consumerTag, sig);
    context.log(
        Activity.level(Severity.ERROR)
            .tag("Subscription")
            .message("Consumer shutdown: " + consumerTag + sig));
  }

  private RabbitMqInboundResult prepareVariables(
      String consumerTag, BasicProperties rawProperties, byte[] body) {

    try {
      String bodyAsString = new String(body, StandardCharsets.UTF_8);
      Object bodyAsObject;
      if (isPayloadJson(bodyAsString)) {
        JsonNode bodyAsJsonElement =
            ObjectMapperSupplier.instance().readValue(bodyAsString, JsonNode.class);
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
      RabbitMqInboundMessage message =
          new RabbitMqInboundMessage(
              consumerTag, bodyAsObject, AMQPPropertyUtil.toProperties(rawProperties));
      return new RabbitMqInboundResult(message);

    } catch (Exception e) {
      LOGGER.error("Failed to parse AMQP message body: {}", e.getMessage());
      throw new ConnectorInputException(e);
    }
  }

  private boolean isPayloadJson(final String bodyAsString) {
    try {
      ObjectMapperSupplier.instance().readValue(bodyAsString, JsonNode.class);
    } catch (JsonProcessingException e) {
      return false;
    }
    return true;
  }
}
