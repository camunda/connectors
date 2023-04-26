/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound.model;

import com.rabbitmq.client.AMQP;
import java.util.Objects;

/** Model of the Connector output */
public class RabbitMqInboundResult {
  private final RabbitMqInboundMessage message;

  public static class RabbitMqInboundMessage {
    private final String consumerTag;
    private final Object body;
    private final AMQP.BasicProperties properties;

    public RabbitMqInboundMessage(
        String consumerTag, Object body, AMQP.BasicProperties properties) {
      this.consumerTag = consumerTag;
      this.body = body;
      this.properties = properties;
    }

    public String getConsumerTag() {
      return consumerTag;
    }

    public Object getBody() {
      return body;
    }

    public AMQP.BasicProperties getProperties() {
      return properties;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RabbitMqInboundMessage that = (RabbitMqInboundMessage) o;
      return Objects.equals(consumerTag, that.consumerTag)
          && Objects.equals(body, that.body)
          && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
      return Objects.hash(consumerTag, body, properties);
    }

    @Override
    public String toString() {
      return "RabbitMqInboundMessage{"
          + "consumerTag='"
          + consumerTag
          + '\''
          + ", body="
          + body
          + ", properties="
          + properties
          + '}';
    }
  }

  public RabbitMqInboundResult(final RabbitMqInboundMessage message) {
    this.message = message;
  }

  public RabbitMqInboundMessage getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RabbitMqInboundResult that = (RabbitMqInboundResult) o;
    return Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message);
  }

  @Override
  public String toString() {
    return "RabbitMqInboundResult{" + "message=" + message + '}';
  }
}
