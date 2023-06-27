/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class SnsWebhookProcessingResult implements WebhookProcessingResult {

  private Map<String, Object> body;
  private Map<String, String> headers;
  private Map<String, String> params;
  private Map<String, Object> connectorData;

  public SnsWebhookProcessingResult() {}

  public SnsWebhookProcessingResult(
      Map<String, Object> body,
      Map<String, String> headers,
      Map<String, String> params,
      Map<String, Object> connectorData) {
    this.body = body;
    this.headers = headers;
    this.params = params;
    this.connectorData = connectorData;
  }

  @Override
  public Map<String, Object> body() {
    return Optional.ofNullable(body).orElse(Collections.emptyMap());
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
}
