/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "OLLaMa", id = OllamaEmbeddingModel.OLLAMA_MODEL_PROVIDER)
public record OllamaEmbeddingModel(
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "baseUrl",
            label = "Base URL",
            description = "OLLaMa base URL")
        String baseUrl,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "ollamaModelName",
            label = "Model name",
            description =
                "OLLaMa model name or identifier. Model must to be classified as Large Language Model and support embedding")
        String modeName)
    implements EmbeddingModelProvider {
  @TemplateProperty(ignore = true)
  public static final String OLLAMA_MODEL_PROVIDER = "OLLAMA_MODEL_PROVIDER";
}
