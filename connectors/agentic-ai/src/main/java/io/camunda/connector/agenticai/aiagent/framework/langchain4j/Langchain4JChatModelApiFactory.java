/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Set;

/**
 * Bridge factory that produces a {@link Langchain4JChatModelApi} for every built-in {@link
 * ProviderConfiguration} subtype. Until per-family native implementations ship, every chat call
 * lands here.
 */
public class Langchain4JChatModelApiFactory implements ChatModelApiFactory<ProviderConfiguration> {

  public static final String API_FAMILY = "langchain4j";

  private static final Set<String> SUPPORTED_PROVIDER_TYPES =
      Set.of(
          AnthropicProviderConfiguration.ANTHROPIC_ID,
          BedrockProviderConfiguration.BEDROCK_ID,
          AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID,
          GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
          OpenAiProviderConfiguration.OPENAI_ID,
          OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID);

  private final ChatModelFactory chatModelFactory;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  public Langchain4JChatModelApiFactory(
      ChatModelFactory chatModelFactory,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.chatModelFactory = chatModelFactory;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  @Override
  public String apiFamily() {
    return API_FAMILY;
  }

  @Override
  public Set<String> supportedProviderTypes() {
    return SUPPORTED_PROVIDER_TYPES;
  }

  @Override
  public ChatModelApi create(ProviderConfiguration configuration) {
    final var chatModel = chatModelFactory.createChatModel(configuration);
    return new Langchain4JChatModelApi(
        chatModel, chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }
}
