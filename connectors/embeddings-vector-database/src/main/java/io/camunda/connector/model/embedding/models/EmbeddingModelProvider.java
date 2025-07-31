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

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "modelProvider")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = BedrockEmbeddingModelProvider.class,
      name = BedrockEmbeddingModelProvider.BEDROCK_MODEL_PROVIDER),
  @JsonSubTypes.Type(
      value = OpenAiEmbeddingModelProvider.class,
      name = OpenAiEmbeddingModelProvider.OPEN_AI_MODEL_PROVIDER),
  @JsonSubTypes.Type(
      value = AzureOpenAiEmbeddingModelProvider.class,
      name = AzureOpenAiEmbeddingModelProvider.AZURE_OPEN_AI_MODEL_PROVIDER)
})
@TemplateDiscriminatorProperty(
    name = "modelProvider",
    id = "modelProvider",
    group = "embeddingModel",
    defaultValue = BedrockEmbeddingModelProvider.BEDROCK_MODEL_PROVIDER,
    label = "Model provider",
    description = "Select embedding model provider")
public sealed interface EmbeddingModelProvider
    permits AzureOpenAiEmbeddingModelProvider,
        BedrockEmbeddingModelProvider,
        OpenAiEmbeddingModelProvider {}
