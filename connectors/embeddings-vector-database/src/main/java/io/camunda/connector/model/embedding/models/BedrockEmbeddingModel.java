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

@TemplateSubType(label = "AWS Bedrock", id = EmbeddingModelProvider.BEDROCK_MODEL_PROVIDER)
public record BedrockEmbeddingModel(
    @NotBlank
        @TemplateProperty(
            group = "model",
            id = "embeddingModel.bedrockModelName",
            label = "Model name",
            description = "OLLaMa model name or identifier")
        String modeName)
    implements EmbeddingModelProvider {}
