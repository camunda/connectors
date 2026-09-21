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
import org.jspecify.annotations.Nullable;

/** Reusable, saved credential for the OpenAI {@code openai-api} backend. */
@Configuration(
    id = "io.camunda:agentic-ai-openai-api-credential:1",
    version = 1,
    name = "OpenAI API Credential")
public record OpenAiApiCredential(
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "OpenAI API key",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey,
    @TemplateProperty(
            group = "provider",
            label = "Organization ID",
            description =
                "For members of multiple organizations. Details in the <a"
                    + " href=\"https://platform.openai.com/docs/api-reference/authentication\""
                    + " target=\"_blank\">documentation</a>.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            optional = true)
        @Nullable String organizationId,
    @TemplateProperty(
            group = "provider",
            label = "Project ID",
            description =
                "For accounts with multiple projects. Details in the <a"
                    + " href=\"https://platform.openai.com/docs/api-reference/authentication\""
                    + " target=\"_blank\">documentation</a>.",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            optional = true)
        @Nullable String projectId) {

  @Override
  public String toString() {
    return "OpenAiApiCredential{apiKey=[REDACTED], organizationId="
        + organizationId
        + ", projectId="
        + projectId
        + "}";
  }
}
