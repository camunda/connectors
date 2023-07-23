/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class SnsWebhookProcessingResult implements WebhookResult {

  private MappedHttpRequest request;
  private Map<String, Object> connectorData;

  public SnsWebhookProcessingResult() {}

  public SnsWebhookProcessingResult(MappedHttpRequest request, Map<String, Object> connectorData) {
    this.request = request;
    this.connectorData = connectorData;
  }

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return Optional.ofNullable(connectorData).orElse(Collections.emptyMap());
  }
}
