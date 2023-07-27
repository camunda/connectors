/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.inbound.utils.HttpMethods;
import java.util.function.Function;

public record WebhookConnectorProperties(
    String context,
    String method,
    String shouldValidateHmac,
    String hmacSecret,
    String hmacHeader,
    String hmacAlgorithm,
    @FEEL HMACScope[] hmacScopes,
    WebhookAuthorization auth,
    Function<WebhookResultContext, Object> responseBodyExpression) {

  public WebhookConnectorProperties(WebhookConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context,
        wrapper.inbound.method != null ? wrapper.inbound.method : HttpMethods.any.name(),
        wrapper.inbound.shouldValidateHmac,
        wrapper.inbound.hmacSecret,
        wrapper.inbound.hmacHeader,
        wrapper.inbound.hmacAlgorithm,
        // default to BODY if no scopes are provided
        getOrDefault(wrapper.inbound.hmacScopes, new HMACScope[] {HMACScope.BODY}),
        getOrDefault(wrapper.inbound.auth, new WebhookAuthorization.None()),
        wrapper.inbound.responseBodyExpression);
  }

  public record WebhookConnectorPropertiesWrapper(WebhookConnectorProperties inbound) {}

  private static <T> T getOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
