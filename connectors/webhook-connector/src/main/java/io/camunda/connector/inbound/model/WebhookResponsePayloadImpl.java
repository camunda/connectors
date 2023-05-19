/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.inbound.WebhookResponsePayload;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class WebhookResponsePayloadImpl implements WebhookResponsePayload {

  public static final String DEFAULT_RESPONSE_STATUS_KEY = "webhookCallStatus";
  public static final String DEFAULT_RESPONSE_STATUS_VALUE_OK = "OK";
  public static final String DEFAULT_RESPONSE_STATUS_VALUE_FAIL = "FAIL";

  private Map<String, String> headers;
  private Object body;
  private InboundConnectorResult executionResult;

  @Override
  public Map<String, String> headers() {
    return Optional.ofNullable(headers).orElse(Collections.emptyMap());
  }

  public void addHeader(String key, String value) {
    if (headers == null) {
      headers = new HashMap<>();
    }
    headers.put(key, value);
  }

  @Override
  public Object body() {
    return Optional.ofNullable(body)
        .orElse(Map.of(DEFAULT_RESPONSE_STATUS_KEY, DEFAULT_RESPONSE_STATUS_VALUE_OK));
  }

  @Override
  public InboundConnectorResult executionResult() {
    return executionResult;
  }

  public void setExecutionResult(InboundConnectorResult executionResult) {
    this.executionResult = executionResult;
  }

  public void setBody(Object body) {
    this.body = body;
  }
}
