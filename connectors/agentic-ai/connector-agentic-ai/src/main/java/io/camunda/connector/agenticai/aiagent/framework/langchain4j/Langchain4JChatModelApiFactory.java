/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.V1ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.provider.V1ProviderConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Adapts one LangChain4J provider to the chat model SPI. {@link #supports} matches a {@link
 * V1ChatModelApiConfiguration} whose {@link V1ProviderConfiguration#providerType()} equals {@link
 * #providerType()}, and {@link #create} builds the underlying LangChain4J chat model once via
 * {@link #createChatModel} and wraps it in a {@link Langchain4JChatModelApi}. Each built-in
 * provider is a concrete subclass supplying its own discriminator and model construction logic,
 * registered as its own bean so an individual provider's implementation can be swapped without
 * touching the others.
 *
 * @param <T> the {@link V1ProviderConfiguration} subtype this factory handles
 */
public abstract class Langchain4JChatModelApiFactory<T extends V1ProviderConfiguration>
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
        capabilities,
        this::mapTokenUsage);
  }
}
