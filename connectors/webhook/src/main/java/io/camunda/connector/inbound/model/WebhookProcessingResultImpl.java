/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class WebhookProcessingResultImpl implements WebhookResult {

  private MappedHttpRequest request;
  private Map<String, Object> connectorData;

  private Function<WebhookResultContext, Object> responseBodyExpression;

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return Optional.ofNullable(connectorData).orElse(Collections.emptyMap());
  }

  @Override
  public Function<WebhookResultContext, Object> responseBodyExpression() {
    if (responseBodyExpression != null) {
      return responseBodyExpression;
    }
    return (response) -> null;
  }

  public void setRequest(MappedHttpRequest request) {
    this.request = request;
  }

  public void setConnectorData(Map<String, Object> connectorData) {
    this.connectorData = connectorData;
  }

  public void setResponseBodyExpression(
      Function<WebhookResultContext, Object> responseBodyExpression) {
    this.responseBodyExpression = responseBodyExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WebhookProcessingResultImpl that = (WebhookProcessingResultImpl) o;
    return Objects.equals(request, that.request)
        && Objects.equals(connectorData, that.connectorData)
        && Objects.equals(responseBodyExpression, that.responseBodyExpression);
  }

  @Override
  public int hashCode() {
    return Objects.hash(request, connectorData, responseBodyExpression);
  }

  @Override
  public String toString() {
    return "WebhookProcessingResultImpl{"
        + "request="
        + request
        + ", connectorData="
        + connectorData
        + ", responseBodyExpression="
        + responseBodyExpression
        + '}';
  }
}
