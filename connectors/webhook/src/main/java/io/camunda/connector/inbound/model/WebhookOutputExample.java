/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.generator.java.annotation.DataExample;
import java.util.Map;

public class WebhookOutputExample {

  @DataExample(feel = "= request.body.orderId")
  public static WebhookOutputExampleContext example() {
    return new WebhookOutputExampleContext(
        new MappedHttpRequest(
            Map.of("orderId", "123", "status", "created"),
            Map.of("Content-Type", "application/json"),
            Map.of()));
  }

  /**
   * Mirrors the FEEL evaluation context {@code HttpWebhookExecutable#verify} builds at runtime
   * ({@code Map.of("request", Map.of("body", ..., "headers", ..., "params", ...))}). The tooltip
   * generator evaluates the {@code feel} expression directly against the object returned by the
   * {@link DataExample}-annotated method (its fields become the top-level FEEL context), so the
   * example must expose a {@code request} field itself, rather than returning {@link
   * MappedHttpRequest} directly.
   */
  public record WebhookOutputExampleContext(MappedHttpRequest request) {}
}
