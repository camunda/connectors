/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotBlank;

/**
 * Reusable credential holding the API key an incoming request must present. How the key is read
 * from the request (the locator) depends on the sending service, so it stays on the element.
 */
@Configuration(
    id = "io.camunda.connectors:webhook-api-key:1",
    version = 1,
    name = "Webhook API Key")
public record WebhookApiKeyConfiguration(
    @NotBlank
        @TemplateProperty(
            group = "authorization",
            label = "API key",
            description = "Expected API key",
            secret = true,
            constraints = @PropertyConstraints(notEmpty = true))
        String apiKey) {

  @Override
  public String toString() {
    return "WebhookApiKeyConfiguration{apiKey=[REDACTED]}";
  }
}
