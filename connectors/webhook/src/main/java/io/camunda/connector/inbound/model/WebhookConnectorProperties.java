/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.impl.feel.FEEL;
import java.util.Map;
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
    Function<Object, Map> responseBodyExpression) {

  public WebhookConnectorProperties(WebhookConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context,
        wrapper.inbound.method,
        wrapper.inbound.shouldValidateHmac,
        wrapper.inbound.hmacSecret,
        wrapper.inbound.hmacHeader,
        wrapper.inbound.hmacAlgorithm,
        // default to BODY if no scopes are provided
        wrapper.inbound.hmacScopes != null
            ? wrapper.inbound.hmacScopes
            : new HMACScope[] {HMACScope.BODY},
        wrapper.inbound.auth,
        wrapper.inbound.responseBodyExpression);
  }

  public record WebhookConnectorPropertiesWrapper(WebhookConnectorProperties inbound) {}
}
