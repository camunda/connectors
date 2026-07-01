/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import java.util.Map;

public class A2aWebhookResult implements WebhookResult {

  private final MappedHttpRequest request;

  public A2aWebhookResult(MappedHttpRequest request) {
    this.request = request;
  }

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return Map.of();
  }
}
