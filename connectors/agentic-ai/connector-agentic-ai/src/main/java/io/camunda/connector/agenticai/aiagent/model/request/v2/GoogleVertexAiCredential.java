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

/**
 * Reusable, saved credential for the Gemini {@code google-vertex-ai} backend's {@code
 * serviceAccountCredentials} authentication.
 */
@Configuration(
    id = "io.camunda:agentic-ai-google-vertex-ai-credential:1",
    version = 1,
    name = "Google Vertex AI Credential")
public record GoogleVertexAiCredential(
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "JSON key of the service account",
            description = "This is the key of the service account in JSON format.",
            feel = FeelMode.optional,
            secret = true,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String jsonKey) {

  @Override
  public String toString() {
    return "GoogleVertexAiCredential{jsonKey=[REDACTED]}";
  }
}
