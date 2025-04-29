/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "modelProvider")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = OllamaEmbeddingModel.class,
      name = OllamaEmbeddingModel.OLLAMA_MODEL_PROVIDER),
  @JsonSubTypes.Type(
      value = BedrockEmbeddingModel.class,
      name = BedrockEmbeddingModel.BEDROCK_MODEL_PROVIDER),
})
@TemplateDiscriminatorProperty(
    name = "modelProvider",
    id = "modelProvider",
    group = "embeddingModel",
    defaultValue = BedrockEmbeddingModel.BEDROCK_MODEL_PROVIDER,
    label = "Model provider",
    description = "Select embedding model provider")
@TemplateSubType(label = "Model provider", id = "modelProvider")
public sealed interface EmbeddingModelProvider
    permits BedrockEmbeddingModel, OllamaEmbeddingModel {}
