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

public record SnsWebhookProcessingResult(
    MappedHttpRequest request, Map<String, Object> connectorData) implements WebhookResult {

  @Override
  public Map<String, Object> connectorData() {
    return Optional.ofNullable(connectorData).orElse(Collections.emptyMap());
  }
}
