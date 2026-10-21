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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ToolCallMetadataCodec;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Base class for a {@link ChatModelFactory} that backs its chat models with LangChain4j. A subclass
 * supplies the configuration type, {@link #supports} and {@link #createChatModel}, and can override
 * the tool call metadata hooks to carry provider data such as thought signatures.
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

  /**
   * Creates a {@link CloseableChatModel} instance from the given configuration. The connector
   * creates one model per request and closes it afterwards.
   */
  protected abstract CloseableChatModel createChatModel(T configuration);

  /**
   * Whether this factory serves the configuration. Match only the configurations that this factory
   * owns: the registry fails when no factory or more than one factory matches. The configuration is
   * not necessarily of the type {@code T}, so check the type first.
   */
  @Override
  public abstract boolean supports(ChatModelConfiguration configuration);

  /**
   * Extracts the provider data to persist on one tool call from the attributes of a response, for
   * example the thought signature of the call. The default carries nothing. The connector persists
   * the result under the provider ID of the configuration, and returns it to {@link
   * #readToolCallMetadata} only for the same provider.
   *
   * @param aiMessageAttributes the full, flat attributes of the {@code AiMessage} (never empty)
   * @return the data to persist for the tool call, empty if there is none
   */
  protected Map<String, Object> writeToolCallMetadata(
      T configuration, String toolCallId, Map<String, Object> aiMessageAttributes) {
    return Map.of();
  }

  /**
   * Restores the attributes of a replayed {@code AiMessage} from the data that {@link
   * #writeToolCallMetadata} persisted for one tool call. The default carries nothing.
   *
   * @param providerMetadata the persisted data of the tool call (never empty, untyped since it
   *     round-tripped through a process variable)
   * @return the entries to put on the attributes of the replayed message, keyed the way LangChain4j
   *     expects. LangChain4j keeps one attribute map per message, so the entries of all tool calls
   *     of the message are merged into it: make each key unique per tool call, for example by
   *     including the tool call ID
   */
  protected Map<String, Object> readToolCallMetadata(
      T configuration, String toolCallId, Map<?, ?> providerMetadata) {
    return Map.of();
  }

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

  @Override
  public final ChatModel create(ChatModelConfiguration configuration) {
    if (!configurationType.isInstance(configuration)) {
      throw new IllegalArgumentException(
          "%s cannot create a chat model for a configuration of type %s, expected %s"
              .formatted(
                  getClass().getSimpleName(),
                  configuration.getClass().getName(),
                  configurationType.getName()));
    }

    final var typedConfiguration = configurationType.cast(configuration);
    final var toolCallMetadataCodec =
        new ToolCallMetadataCodec(
            configuration.provider(),
            (toolCallId, attributes) ->
                writeToolCallMetadata(typedConfiguration, toolCallId, attributes),
            (toolCallId, providerMetadata) ->
                readToolCallMetadata(typedConfiguration, toolCallId, providerMetadata));

    return new LangChain4JChatModel(
        createChatModel(typedConfiguration),
        toolCallMetadataCodec,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter,
        this::mapTokenUsage);
  }
}
