/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqConnectionConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record RabbitMqRequest(
    @Valid
        @TemplateProperty(
            id = RabbitMqConnectionConfiguration.CHOOSER_ID,
            label = "Connection credential",
            group = "authentication",
            type = TemplateProperty.PropertyType.Configuration,
            optional = true,
            tooltip =
                "Choose a reusable RabbitMQ connection credential, or configure one-time connection parameters below.")
        RabbitMqConnectionConfiguration rabbitMqConnection,
    // Not @Valid on the component: Modeler leaves the inline authentication's default
    // discriminator in the job input even after a credential is bound, and cascading validation
    // into that leftover object would fail its @NotBlank members. See
    // getInlineAuthenticationWhenNoCredentialBound() below.
    @NestedProperties(
            condition =
                @PropertyCondition(
                    property = RabbitMqConnectionConfiguration.CHOOSER_ID,
                    isEmpty = NullableBoolean.TRUE))
        RabbitMqAuthentication authentication,
    @Valid @NotNull RabbitMqOutboundRouting routing,
    @Valid @NotNull RabbitMqMessage message) {

  public RabbitMqRequest(
      RabbitMqAuthentication authentication,
      RabbitMqOutboundRouting routing,
      RabbitMqMessage message) {
    this(null, authentication, routing, message);
  }

  /** The bound credential wins over the inline authentication. */
  @Override
  public RabbitMqAuthentication authentication() {
    return rabbitMqConnection != null ? rabbitMqConnection.authentication() : authentication;
  }

  /**
   * The exchange and routing key always come from the task; the host, port and virtual host come
   * from the bound credential when there is one.
   */
  @Override
  public RabbitMqOutboundRouting routing() {
    return rabbitMqConnection != null && routing != null
        ? new RabbitMqOutboundRouting(
            routing.exchange(), routing.routingKey(), rabbitMqConnection.routing())
        : routing;
  }

  @AssertTrue(
      message = "No authentication provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return authentication() != null;
  }

  /** Validates the inline authentication only when no credential is bound. */
  @Valid
  @JsonIgnore
  public RabbitMqAuthentication getInlineAuthenticationWhenNoCredentialBound() {
    return rabbitMqConnection != null ? null : authentication;
  }

  @JsonIgnore
  @AssertFalse
  public boolean isRoutingParamsNotFilling() {
    var effectiveRouting = routing();
    return !FactoryRoutingData.isPresentWhenRequired(
        authentication(), effectiveRouting != null ? effectiveRouting.routingData() : null);
  }
}
