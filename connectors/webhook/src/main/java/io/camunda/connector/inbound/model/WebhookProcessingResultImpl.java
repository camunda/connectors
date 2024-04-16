/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public record WebhookProcessingResultImpl(
    MappedHttpRequest request,
    Function<WebhookResultContext, WebhookHttpResponse> responseExpression,
    Map<String, Object> connectorData)
    implements WebhookResult {

  public WebhookProcessingResultImpl {}

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return Optional.ofNullable(connectorData).orElse(Collections.emptyMap());
  }

  @Override
  public Function<WebhookResultContext, WebhookHttpResponse> response() {
    return responseExpression;
  }
}
