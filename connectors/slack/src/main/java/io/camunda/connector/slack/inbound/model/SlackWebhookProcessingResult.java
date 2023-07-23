/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import java.util.Map;

public class SlackWebhookProcessingResult implements WebhookResult {

  private MappedHttpRequest request;
  private final Map<String, Object> connectorData;

  private WebhookHttpResponse response;

  public SlackWebhookProcessingResult(
      MappedHttpRequest request, Map<String, Object> connectorData, WebhookHttpResponse response) {
    this.request = request;
    this.connectorData = connectorData;
    this.response = response;
  }

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return connectorData;
  }

  @Override
  public WebhookHttpResponse response() {
    return response;
  }
}
