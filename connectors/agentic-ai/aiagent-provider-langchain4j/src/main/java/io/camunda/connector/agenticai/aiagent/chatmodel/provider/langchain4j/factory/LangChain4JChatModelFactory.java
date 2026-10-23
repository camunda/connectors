/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.LangChain4JChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Base class for a {@link ChatModelFactory} that backs its chat models with LangChain4j. {@link
 * #supports} matches a configuration of the type {@code T}, {@link #create} builds the underlying
 * LangChain4j chat model through {@link #createChatModel} and wraps it in a {@link
 * LangChain4JChatModel}, which converts the conversation, the tool definitions and the response
 * with the LangChain4j converters. The class contains no vendor specific code: a subclass supplies
 * the configuration type and the construction of the LangChain4j model, and is registered as a
 * bean.
 *
 * @param <T> the {@link ChatModelConfiguration} subtype this factory handles
 */
public abstract class LangChain4JChatModelFactory<T extends ChatModelConfiguration>
    implements ChatModelFactory {

  private final Class<T> configurationType;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  protected LangChain4JChatModelFactory(
      Class<T> configurationType,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.configurationType = configurationType;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  /** Creates a {@link CloseableChatModel} instance from the given configuration. */
  public abstract CloseableChatModel createChatModel(T configuration);

  /**
   * Maps the base input/output counts on every {@link TokenUsage}. Subclasses override this to
   * layer on cache and reasoning token detail exposed by the provider-specific {@link TokenUsage}
   * subclass their LangChain4j client returns.
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

  /**
   * Matches the configurations of type {@code T}. A subclass can override it to narrow the match.
   */
  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configurationType.isInstance(configuration);
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var chatModel = createChatModel(configurationType.cast(configuration));

    return new LangChain4JChatModel(
        chatModel,
        configuration,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        this::mapTokenUsage);
  }
}
