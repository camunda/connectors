/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import io.camunda.connector.rabbitmq.supplier.GsonSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.text.StringEscapeUtils;
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
    try {
      RabbitMqInboundResult variables = prepareVariables(consumerTag, envelope, properties, body);
      InboundConnectorResult<?> result = context.correlate(variables);

      if (result != null && result.isActivated()) {
        LOGGER.debug("ACK - inbound event correlated successfully: {}", result.getResponseData());
        getChannel().basicAck(envelope.getDeliveryTag(), false);
      } else {
        LOGGER.debug("NACK (no requeue) - inbound event not correlated: {}", result.getErrorData());
        getChannel().basicReject(envelope.getDeliveryTag(), false);
      }

    } catch (ConnectorInputException e) {
      LOGGER.warn("NACK (no requeue) - failed to parse AMQP message body: {}", e.getMessage());
      getChannel().basicReject(envelope.getDeliveryTag(), false);
    } catch (Exception e) {
      LOGGER.debug("NACK (requeue) - failed to correlate event", e);
      getChannel().basicReject(envelope.getDeliveryTag(), true);
    }
  }

  @Override
  public void handleCancel(String consumerTag) {
    LOGGER.info("Consumer cancelled: {}", consumerTag);
    try {
      context.cancel(null);
    } catch (Exception e) {
      LOGGER.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    LOGGER.error("Consumer shutdown: {}", consumerTag, sig);
    try {
      context.cancel(sig);
    } catch (Exception e) {
      LOGGER.error("Failed to cancel Connector execution: {}", e.getMessage());
    }
  }

  private RabbitMqInboundResult prepareVariables(
      String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {

    try {
      String bodyAsString = new String(body, StandardCharsets.UTF_8);
      JsonElement bodyAsJsonElement =
          GsonSupplier.gson()
              .fromJson(StringEscapeUtils.unescapeJson(bodyAsString), JsonElement.class);

      Object bodyAsObject;
      if (bodyAsJsonElement instanceof JsonPrimitive) {
        JsonPrimitive bodyAsPrimitive = (JsonPrimitive) bodyAsJsonElement;
        if (bodyAsPrimitive.isBoolean()) {
          bodyAsObject = bodyAsPrimitive.getAsBoolean();
        } else if (bodyAsPrimitive.isNumber()) {
          bodyAsObject = bodyAsPrimitive.getAsNumber();
        } else {
          bodyAsObject = bodyAsPrimitive.getAsString();
        }
      } else {
        bodyAsObject = GsonSupplier.gson().fromJson(bodyAsJsonElement, Object.class);
      }
      RabbitMqInboundMessage message =
          new RabbitMqInboundMessage(consumerTag, bodyAsObject, properties);
      return new RabbitMqInboundResult(message);

    } catch (Exception e) {
      LOGGER.error("Failed to parse AMQP message body: {}", e.getMessage());
      throw new ConnectorInputException(e);
    }
  }
}
