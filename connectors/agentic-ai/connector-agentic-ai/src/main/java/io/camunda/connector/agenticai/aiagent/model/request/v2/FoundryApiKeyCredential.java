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

/** Reusable, saved credential for the Microsoft Foundry {@code apiKey} authentication variant. */
@Configuration(
    id = "io.camunda:agentic-ai-foundry-api-key-credential:1",
    version = 1,
    name = "Microsoft Foundry API Key Credential")
public record FoundryApiKeyCredential(
    @NotBlank
        @TemplateProperty(
            group = "provider",
            label = "API key",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            secret = true,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey) {

  @Override
  public String toString() {
    return "FoundryApiKeyCredential{apiKey=[REDACTED]}";
  }
}
