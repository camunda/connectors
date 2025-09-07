/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BEDROCK_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OPENAI_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AnthropicProviderConfiguration.class, name = ANTHROPIC_ID),
  @JsonSubTypes.Type(value = BedrockProviderConfiguration.class, name = BEDROCK_ID),
  @JsonSubTypes.Type(value = AzureOpenAiProviderConfiguration.class, name = AZURE_OPENAI_ID),
  @JsonSubTypes.Type(value = GoogleVertexAiProviderConfiguration.class, name = GOOGLE_VERTEX_AI_ID),
  @JsonSubTypes.Type(value = OpenAiProviderConfiguration.class, name = OPENAI_ID),
  @JsonSubTypes.Type(
      value = OpenAiCompatibleProviderConfiguration.class,
      name = OPENAI_COMPATIBLE_ID)
})
@TemplateDiscriminatorProperty(
    label = "Provider",
    group = "provider",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = ANTHROPIC_ID)
public sealed interface ProviderConfiguration
    permits AnthropicProviderConfiguration,
        BedrockProviderConfiguration,
        AzureOpenAiProviderConfiguration,
        GoogleVertexAiProviderConfiguration,
        OpenAiProviderConfiguration,
        OpenAiCompatibleProviderConfiguration {}
