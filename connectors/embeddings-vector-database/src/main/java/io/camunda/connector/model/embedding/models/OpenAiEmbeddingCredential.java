/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/** Reusable, secrets-only credential for the OpenAI embedding model provider. */
@Configuration(
    id = "io.camunda.connectors:embeddings-openai-credential:1",
    version = 1,
    name = "OpenAI embedding credential")
public record OpenAiEmbeddingCredential(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            label = "OpenAI API key",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey) {

  @Override
  public String toString() {
    return "OpenAiEmbeddingCredential{apiKey=[REDACTED]}";
  }
}
