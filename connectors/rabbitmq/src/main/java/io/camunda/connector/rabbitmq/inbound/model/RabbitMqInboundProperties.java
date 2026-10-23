/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqConnectionConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Objects;

public class RabbitMqInboundProperties {

  /**
   * Reusable connection credential; when bound, its authentication and routing data take precedence
   * over the inline ones (see {@link #getAuthentication()} and {@link #getRouting()}). Declared
   * first so it renders before the fields it gates, as required by ConditionPropertyOrderRule.
   */
  @FEEL
  @Valid
  @TemplateProperty(
      id = RabbitMqConnectionConfiguration.CHOOSER_ID,
      label = "Connection credential",
      group = "authentication",
      type = TemplateProperty.PropertyType.Configuration,
      optional = true,
      feel = FeelMode.disabled,
      tooltip =
          "Choose a reusable RabbitMQ connection credential, or configure one-time connection parameters below.")
  private RabbitMqConnectionConfiguration rabbitMqConnection;

  // Neither inline field is @Valid: Modeler leaves the inline authentication's default
  // discriminator in place even after a credential is bound, and cascading validation into that
  // leftover would fail its @NotBlank members. They are validated through
  // getInlineAuthenticationWhenNoCredentialBound() and getInlineRoutingWhenNoCredentialBound().
  @NestedProperties(
      condition =
          @PropertyCondition(
              property = RabbitMqConnectionConfiguration.CHOOSER_ID,
              isEmpty = NullableBoolean.TRUE))
  private RabbitMqAuthentication authentication;

  @NestedProperties(
      condition =
          @PropertyCondition(
              property = RabbitMqConnectionConfiguration.CHOOSER_ID,
              isEmpty = NullableBoolean.TRUE))
  private FactoryRoutingData routing;

  @NotBlank
  @TemplateProperty(
      label = "Queue name",
      group = "subscription",
      tooltip = "Name of the queue to subscribe to")
  private String queueName;

  @TemplateProperty(label = "Consumer tag", group = "subscription")
  private String consumerTag;

  @Valid
  @TemplateProperty(
      label = "Arguments",
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

  public RabbitMqConnectionConfiguration getRabbitMqConnection() {
    return rabbitMqConnection;
  }

  public void setRabbitMqConnection(RabbitMqConnectionConfiguration rabbitMqConnection) {
    this.rabbitMqConnection = rabbitMqConnection;
  }

  /** The bound credential wins over the inline authentication. */
  public RabbitMqAuthentication getAuthentication() {
    return rabbitMqConnection != null ? rabbitMqConnection.authentication() : authentication;
  }

  public void setAuthentication(RabbitMqAuthentication authentication) {
    this.authentication = authentication;
  }

  /** The bound credential wins over the inline routing data. */
  public FactoryRoutingData getRouting() {
    return rabbitMqConnection != null ? rabbitMqConnection.routing() : routing;
  }

  @AssertTrue(
      message = "No authentication provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return getAuthentication() != null;
  }

  /** Validates the inline authentication only when no credential is bound. */
  @Valid
  @JsonIgnore
  public RabbitMqAuthentication getInlineAuthenticationWhenNoCredentialBound() {
    return rabbitMqConnection != null ? null : authentication;
  }

  /** Validates the inline routing data only when no credential is bound. */
  @Valid
  @JsonIgnore
  public FactoryRoutingData getInlineRoutingWhenNoCredentialBound() {
    return rabbitMqConnection != null ? null : routing;
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
        && Objects.equals(rabbitMqConnection, that.rabbitMqConnection)
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(routing, that.routing)
        && Objects.equals(queueName, that.queueName)
        && Objects.equals(consumerTag, that.consumerTag)
        && Objects.equals(arguments, that.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        rabbitMqConnection, authentication, routing, queueName, consumerTag, arguments, exclusive);
  }

  @Override
  public String toString() {
    return "RabbitMqInboundProperties{"
        + "rabbitMqConnection="
        + rabbitMqConnection
        + ", authentication="
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
