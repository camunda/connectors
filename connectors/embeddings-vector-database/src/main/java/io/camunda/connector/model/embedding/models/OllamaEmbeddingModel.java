/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import static io.camunda.connector.model.embedding.models.EmbeddingModelProvider.OLLAMA_MODEL_PROVIDER;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "OLLaMa", id = OLLAMA_MODEL_PROVIDER)
public record OllamaEmbeddingModel(
    @NotBlank
        @TemplateProperty(
            group = "model",
            id = "embeddingModel.baseUrl",
            label = "Base URL",
            description = "OLLaMa base URL")
        String baseUrl,
    @NotBlank
        @TemplateProperty(
            group = "model",
            id = "embeddingModel.ollamaModelName",
            label = "Model name",
            description = "OLLaMa model name or identifier")
        String modeName)
    implements EmbeddingModelProvider {}
