/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Reusable, saved credential for the OpenAI {@code custom} (OpenAI-compatible endpoint) backend.
 *
 * <p>Unlike the other provider credentials, it carries the endpoint and the default model next to
 * the API key: an OpenAI-compatible gateway is identified by all three together, so they are
 * provisioned and rotated as one unit. Headers, query parameters and body properties stay
 * diagram-level configuration.
 */
@Configuration(
    id = "io.camunda:agentic-ai-openai-custom-credential:1",
    version = 1,
    name = "OpenAI-compatible Credential")
public record OpenAiCustomCredential(
    @NotBlank
        @HttpUrl
        @TemplateProperty(
            group = "provider",
            label = "API endpoint",
            description =
                "Base URL of the OpenAI-compatible API; <code>/chat/completions</code> or <code>/responses</code> will be appended depending on the selected API.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            placeholder = "https://api.openai.com/v1",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String endpoint,
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "API key",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            secret = true,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey,
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "Model",
            description = "The model ID served by the endpoint.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String model) {

  @Override
  public String toString() {
    return "OpenAiCustomCredential{endpoint="
        + endpoint
        + ", apiKey=[REDACTED], model="
        + model
        + "}";
  }
}
