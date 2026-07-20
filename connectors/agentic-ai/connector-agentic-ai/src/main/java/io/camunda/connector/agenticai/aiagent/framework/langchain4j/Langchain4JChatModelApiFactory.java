/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Adapts one {@link ChatModelProvider} to the chat model SPI. {@link #supports} matches a {@link
 * ProviderChatModelApiConfiguration} whose {@link ProviderConfiguration#providerType()} equals the
 * wrapped provider's {@link ChatModelProvider#type()}, and {@link #create} builds the underlying
 * LangChain4J chat model once via that provider and wraps it in a {@link Langchain4JChatModelApi}.
 * One instance is registered per built-in provider, so an individual provider's implementation can
 * be swapped without touching the others.
 */
public class Langchain4JChatModelApiFactory implements ChatModelApiFactory {

  private final ChatModelProvider<?> chatModelProvider;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final ModelCapabilities capabilities;

  public Langchain4JChatModelApiFactory(
      ChatModelProvider<?> chatModelProvider,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ModelCapabilities capabilities) {
    this.chatModelProvider = chatModelProvider;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.capabilities = capabilities;
  }

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof ProviderChatModelApiConfiguration provider
        && chatModelProvider.type().equals(provider.providerConfiguration().providerType());
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var providerConfiguration =
        ((ProviderChatModelApiConfiguration) configuration).providerConfiguration();

    @SuppressWarnings("unchecked")
    final var provider = (ChatModelProvider<ProviderConfiguration>) chatModelProvider;
    final var chatModel = provider.createChatModel(providerConfiguration);

    return new Langchain4JChatModelApi(
        chatModel,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        capabilities);
  }
}
