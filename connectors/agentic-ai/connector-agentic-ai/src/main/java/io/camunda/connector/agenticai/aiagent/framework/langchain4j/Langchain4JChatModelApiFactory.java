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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.function.Predicate;

/**
 * Factory for one built-in provider routed through LangChain4J: {@link #supports} matches a {@link
 * ProviderChatModelApiConfiguration} whose {@link ProviderConfiguration} satisfies the given
 * predicate, and {@link #create} builds the underlying LangChain4J chat model once via the {@link
 * ChatModelFactory} and wraps it in a {@link Langchain4JChatModelApi}. One instance is registered
 * per provider, each with a predicate keyed to that provider's {@link ProviderConfiguration}
 * subtype, so an individual provider's implementation can be swapped without touching the others.
 */
public class Langchain4JChatModelApiFactory implements ChatModelApiFactory {

  private final Predicate<ProviderConfiguration> supportedProvider;
  private final ChatModelFactory chatModelFactory;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final ModelCapabilities capabilities;

  public Langchain4JChatModelApiFactory(
      Predicate<ProviderConfiguration> supportedProvider,
      ChatModelFactory chatModelFactory,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ModelCapabilities capabilities) {
    this.supportedProvider = supportedProvider;
    this.chatModelFactory = chatModelFactory;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.capabilities = capabilities;
  }

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof ProviderChatModelApiConfiguration provider
        && supportedProvider.test(provider.providerConfiguration());
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var providerConfiguration =
        ((ProviderChatModelApiConfiguration) configuration).providerConfiguration();
    final var chatModel = chatModelFactory.createChatModel(providerConfiguration);
    return new Langchain4JChatModelApi(
        chatModel,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        capabilities);
  }
}
