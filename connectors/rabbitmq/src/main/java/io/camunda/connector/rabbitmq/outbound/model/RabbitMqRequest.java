/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.rabbitmq.common.model.CredentialsAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.UriAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;

public record RabbitMqRequest(
    @Valid @NotNull RabbitMqAuthentication authentication,
    @Valid @NotNull RabbitMqOutboundRouting routing,
    @Valid @NotNull RabbitMqMessage message) {

  @JsonIgnore
  @AssertFalse
  public boolean isRoutingParamsNotFilling() {
    if (authentication instanceof UriAuthentication) {
      // not need check routing when we use URI auth type
      return false;
    }
    if (authentication instanceof CredentialsAuthentication && routing != null) {
      return routing.routingData().port() == null
          || routing.routingData().port().isBlank()
          || routing.routingData().hostName() == null
          || routing.routingData().hostName().isBlank()
          || routing.routingData().virtualHost() == null
          || routing.routingData().virtualHost().isBlank();
    }
    return true;
  }
}
