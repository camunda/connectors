/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.common.model.RabbitMqMessage;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;

public class RabbitMqRequest {

  @Valid @NotNull private RabbitMqAuthentication authentication;
  @Valid @NotNull private RabbitMqOutboundRouting routing;
  @Valid @NotNull private RabbitMqMessage message;

  @AssertFalse
  private boolean isRoutingParamsNotFilling() {
    if (authentication.getAuthType() == RabbitMqAuthenticationType.uri) {
      // not need check routing when we use URI auth type
      return false;
    }
    if (authentication.getAuthType() == RabbitMqAuthenticationType.credentials && routing != null) {
      return routing.getPort() == null
          || routing.getPort().isBlank()
          || routing.getHostName() == null
          || routing.getHostName().isBlank()
          || routing.getVirtualHost() == null
          || routing.getVirtualHost().isBlank();
    }
    return true;
  }

  public RabbitMqAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final RabbitMqAuthentication authentication) {
    this.authentication = authentication;
  }

  public RabbitMqOutboundRouting getRouting() {
    return routing;
  }

  public void setRouting(final RabbitMqOutboundRouting routing) {
    this.routing = routing;
  }

  public RabbitMqMessage getMessage() {
    return message;
  }

  public void setMessage(final RabbitMqMessage message) {
    this.message = message;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RabbitMqRequest request = (RabbitMqRequest) o;
    return Objects.equals(authentication, request.authentication)
        && Objects.equals(routing, request.routing)
        && Objects.equals(message, request.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, routing, message);
  }

  @Override
  public String toString() {
    return "RabbitMqRequest{"
        + "authentication="
        + authentication
        + ", routing="
        + routing
        + ", message="
        + message
        + "}";
  }
}
