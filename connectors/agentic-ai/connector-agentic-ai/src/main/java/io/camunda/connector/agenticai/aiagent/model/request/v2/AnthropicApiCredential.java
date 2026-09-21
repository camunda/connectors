/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/** Reusable, saved credential for the Anthropic {@code anthropic-api} backend. */
@Configuration(
    id = "io.camunda:agentic-ai-anthropic-api-credential:1",
    version = 1,
    name = "Anthropic API Credential")
public record AnthropicApiCredential(
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "Anthropic API key",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            secret = true,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey) {

  @Override
  public String toString() {
    return "AnthropicApiCredential{apiKey=[REDACTED]}";
  }
}
