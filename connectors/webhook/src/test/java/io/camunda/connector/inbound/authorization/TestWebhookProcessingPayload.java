/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.util.Map;

public record TestWebhookProcessingPayload(
    String requestURL,
    String method,
    Map<String, Object> body,
    Map<String, String> headers,
    Map<String, String> params,
    byte[] rawBody)
    implements WebhookProcessingPayload {
  TestWebhookProcessingPayload(String token, String body) {
    this(
        null,
        null,
        Map.of(),
        Map.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            "Bearer " + token,
            HttpHeaders.CONTENT_TYPE.toLowerCase(),
            "application/json"),
        null,
        body == null ? null : body.getBytes());
  }
}
