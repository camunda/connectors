/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import com.google.common.base.Objects;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class WebhookProcessingResultImpl implements WebhookProcessingResult {

  private Object body;
  private Map<String, String> headers;
  private Map<String, String> params;
  private Map<String, Object> connectorData;

  @Override
  public Object body() {
    return body;
  }

  @Override
  public Map<String, String> headers() {
    return Optional.ofNullable(headers).orElse(Collections.emptyMap());
  }

  @Override
  public Map<String, String> params() {
    return Optional.ofNullable(params).orElse(Collections.emptyMap());
  }

  @Override
  public Map<String, Object> connectorData() {
    return Optional.ofNullable(connectorData).orElse(Collections.emptyMap());
  }

  public void setBody(Object body) {
    this.body = body;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public void setParams(Map<String, String> params) {
    this.params = params;
  }

  public void setConnectorData(Map<String, Object> connectorData) {
    this.connectorData = connectorData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WebhookProcessingResultImpl that = (WebhookProcessingResultImpl) o;
    return Objects.equal(body, that.body)
        && Objects.equal(headers, that.headers)
        && Objects.equal(params, that.params)
        && Objects.equal(connectorData, that.connectorData);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(body, headers, params, connectorData);
  }

  @Override
  public String toString() {
    return "WebhookResponsePayloadImpl{"
        + "body="
        + body
        + ", headers="
        + headers
        + ", params="
        + params
        + ", connectorData="
        + connectorData
        + '}';
  }
}
