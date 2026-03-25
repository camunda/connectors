/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import io.camunda.connector.api.inbound.webhook.Part;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public record TestWebhookProcessingPayload(
    String requestURL,
    String method,
    Map<String, String> headers,
    Map<String, String> params,
    byte[] rawBody,
    Collection<Part> parts)
    implements WebhookProcessingPayload {
  TestWebhookProcessingPayload(String token, String body) {
    this(
        null,
        null,
        Map.of("authorization", "Bearer " + token, "Content-Type", "application/json"),
        null,
        body == null ? null : body.getBytes(),
        List.of());
  }

  TestWebhookProcessingPayload(Map<String, String> headers) {
    this(null, null, headers, null, null, List.of());
  }
}
