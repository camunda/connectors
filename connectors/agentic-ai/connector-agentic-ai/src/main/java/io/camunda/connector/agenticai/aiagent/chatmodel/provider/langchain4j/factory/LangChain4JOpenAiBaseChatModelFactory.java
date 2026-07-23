/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration;
import java.util.Optional;

/**
 * Shared base for the OpenAI-family factories ({@link OpenAiChatModelFactory}, {@link
 * OpenAiCompatibleChatModelFactory}), both of which return {@link OpenAiTokenUsage} from their
 * LangChain4J client and therefore need to layer the same cached-input and reasoning-output token
 * detail onto the base token usage builder.
 *
 * @param <T> the {@link ProviderConfiguration} subtype this factory handles
 */
public abstract class LangChain4JOpenAiBaseChatModelFactory<T extends ProviderConfiguration>
    extends LangChain4JChatModelFactory<T> {

  protected LangChain4JOpenAiBaseChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    super(chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }

  /**
   * Layers OpenAI's cached-input and reasoning-output token detail onto a base token usage builder.
   *
   * <p>OpenAI's {@code prompt_tokens} (already set as {@code inputTokenCount} by {@link
   * LangChain4JChatModelFactory#baseTokenUsageBuilder(TokenUsage)}) INCLUDES cached tokens, unlike
   * Anthropic/Bedrock, whose input token counts already exclude cache reads. To keep the domain
   * {@code inputTokenCount}/{@code cacheReadTokenCount} disjoint across providers, cached tokens
   * are subtracted back out of {@code inputTokenCount} here.
   */
  protected final AgentMetricsTokenUsageBuilder applyOpenAiTokenUsageDetail(
      AgentMetricsTokenUsageBuilder builder, OpenAiTokenUsage usage) {
    Optional.ofNullable(usage.inputTokensDetails())
        .map(OpenAiTokenUsage.InputTokensDetails::cachedTokens)
        .filter(cachedTokens -> cachedTokens > 0)
        .ifPresent(
            cachedTokens -> {
              builder.cacheReadTokenCount(cachedTokens);
              builder.inputTokenCount(Math.max(0, builder.inputTokenCount() - cachedTokens));
            });
    Optional.ofNullable(usage.outputTokensDetails())
        .map(OpenAiTokenUsage.OutputTokensDetails::reasoningTokens)
        .ifPresent(reasoningTokens -> builder.reasoningTokenCount(nullToZero(reasoningTokens)));
    return builder;
  }
}
