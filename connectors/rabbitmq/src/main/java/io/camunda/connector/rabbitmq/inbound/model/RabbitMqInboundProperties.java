/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqRouting;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class RabbitMqInboundProperties {
  @Valid @NotNull @Secret private RabbitMqAuthentication authentication;
  @Valid @Secret private RabbitMqRouting routing;
  @Secret @NotBlank private String queueName;
  @Secret private String consumerTag;
  @Valid @Secret private Map<String, Object> arguments;
  private boolean exclusive;

  public RabbitMqAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(RabbitMqAuthentication authentication) {
    this.authentication = authentication;
  }

  public RabbitMqRouting getRouting() {
    return routing;
  }

  public void setRouting(RabbitMqRouting routing) {
    this.routing = routing;
  }

  public String getQueueName() {
    return queueName;
  }

  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }

  public String getConsumerTag() {
    return consumerTag;
  }

  public void setConsumerTag(String consumerTag) {
    this.consumerTag = consumerTag;
  }

  public Map<String, Object> getArguments() {
    return arguments;
  }

  public void setArguments(Map<String, Object> arguments) {
    this.arguments = arguments;
  }

  public boolean isExclusive() {
    return exclusive;
  }

  public void setExclusive(boolean exclusive) {
    this.exclusive = exclusive;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RabbitMqInboundProperties that = (RabbitMqInboundProperties) o;
    return exclusive == that.exclusive
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(routing, that.routing)
        && Objects.equals(queueName, that.queueName)
        && Objects.equals(consumerTag, that.consumerTag)
        && Objects.equals(arguments, that.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, routing, queueName, consumerTag, arguments, exclusive);
  }

  @Override
  public String toString() {
    return "RabbitMqInboundProperties{"
        + "authentication="
        + authentication
        + ", routing="
        + routing
        + ", queueName='"
        + queueName
        + '\''
        + ", consumerTag='"
        + consumerTag
        + '\''
        + ", arguments="
        + arguments
        + ", exclusive="
        + exclusive
        + '}';
  }
}
