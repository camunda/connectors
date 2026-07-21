/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.LangChain4JChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Adapts one LangChain4J provider to the chat model SPI. {@link #supports} matches a {@link
 * ProviderConfiguration} whose {@link ProviderConfiguration#provider()} equals {@link
 * #providerType()}, and {@link #create} builds the underlying LangChain4J chat model once via
 * {@link #createChatModel} and wraps it in a {@link LangChain4JChatModel}. Each built-in provider
 * is a concrete subclass supplying its own discriminator and model construction logic, registered
 * as its own bean so an individual provider's implementation can be swapped without touching the
 * others.
 *
 * @param <T> the {@link ProviderConfiguration} subtype this factory handles
 */
public abstract class LangChain4JChatModelFactory<T extends ProviderConfiguration>
    implements ChatModelFactory {

  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  protected LangChain4JChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  /** Identifier of the provider this factory is responsible for. */
  public abstract String providerType();

  /** Creates a {@link CloseableChatModel} instance from the given configuration. */
  public abstract CloseableChatModel createChatModel(T providerConfiguration);

  /**
   * Maps the base input/output counts on every {@link TokenUsage}. Subclasses override this to
   * layer on cache and reasoning token detail exposed by the provider-specific {@link TokenUsage}
   * subclass their LangChain4J client returns.
   */
  protected AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) {
    if (usage == null) {
      return AgentMetrics.TokenUsage.empty();
    }

    return baseTokenUsageBuilder(usage).build();
  }

  /**
   * Base input/output token usage builder, exposed so overrides of {@link #mapTokenUsage} can
   * extend it with provider-specific detail rather than rebuilding it from scratch.
   */
  protected final AgentMetricsTokenUsageBuilder baseTokenUsageBuilder(TokenUsage usage) {
    return AgentMetrics.TokenUsage.builder()
        .inputTokenCount(nullToZero(usage.inputTokenCount()))
        .outputTokenCount(nullToZero(usage.outputTokenCount()));
  }

  protected static int nullToZero(@Nullable Integer value) {
    return value != null ? value : 0;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof ProviderConfiguration provider
        && providerType().equals(provider.provider());
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var providerConfiguration = (ProviderConfiguration) configuration;

    @SuppressWarnings("unchecked")
    final var chatModel = createChatModel((T) providerConfiguration);

    return new LangChain4JChatModel(
        chatModel,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        this::mapTokenUsage);
  }
}
