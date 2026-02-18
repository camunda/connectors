/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;

public class RabbitMqInboundProperties {
  @Valid @NotNull private RabbitMqAuthentication authentication;
  @Valid private FactoryRoutingData routing;

  @NotBlank
  @TemplateProperty(
      label = "Queue name",
      group = "subscription",
      description = "Name of the queue to subscribe to")
  private String queueName;

  @TemplateProperty(
      label = "Consumer tag",
      group = "subscription",
      description = "Consumer tag to use for the subscription")
  private String consumerTag;

  @Valid
  @TemplateProperty(
      label = "Arguments",
      description = "Arguments to use for the subscription",
      group = "subscription",
      optional = true,
      feel = FeelMode.required)
  private Map<String, Object> arguments;

  @TemplateProperty(
      label = "Exclusive consumer",
      group = "subscription",
      type = TemplateProperty.PropertyType.Dropdown,
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "true", label = "Yes"),
        @TemplateProperty.DropdownPropertyChoice(value = "false", label = "No")
      },
      defaultValue = "false")
  private boolean exclusive;

  public RabbitMqAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(RabbitMqAuthentication authentication) {
    this.authentication = authentication;
  }

  public FactoryRoutingData getRouting() {
    return routing;
  }

  public void setRouting(FactoryRoutingData routing) {
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
