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
import io.camunda.connector.agenticai.aiagent.framework.api.V1ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Adapts one LangChain4J provider to the chat model SPI. {@link #supports} matches a {@link
 * V1ChatModelApiConfiguration} whose {@link ProviderConfiguration#providerType()} equals {@link
 * #providerType()}, and {@link #create} builds the underlying LangChain4J chat model once via
 * {@link #createChatModel} and wraps it in a {@link Langchain4JChatModelApi}. Each built-in
 * provider is a concrete subclass supplying its own discriminator and model construction logic,
 * registered as its own bean so an individual provider's implementation can be swapped without
 * touching the others.
 *
 * @param <T> the {@link ProviderConfiguration} subtype this factory handles
 */
public abstract class Langchain4JChatModelApiFactory<T extends ProviderConfiguration>
    implements ChatModelApiFactory {

  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;
  private final ModelCapabilities capabilities;

  protected Langchain4JChatModelApiFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ModelCapabilities capabilities) {
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.capabilities = capabilities;
  }

  /** Identifier of the provider this factory is responsible for. */
  protected abstract String providerType();

  /** Creates a {@link CloseableChatModel} instance from the given configuration. */
  protected abstract CloseableChatModel createChatModel(T providerConfiguration);

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof V1ChatModelApiConfiguration provider
        && providerType().equals(provider.providerConfiguration().providerType());
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var providerConfiguration =
        ((V1ChatModelApiConfiguration) configuration).providerConfiguration();

    @SuppressWarnings("unchecked")
    final var chatModel = createChatModel((T) providerConfiguration);

    return new Langchain4JChatModelApi(
        chatModel,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        capabilities);
  }
}
