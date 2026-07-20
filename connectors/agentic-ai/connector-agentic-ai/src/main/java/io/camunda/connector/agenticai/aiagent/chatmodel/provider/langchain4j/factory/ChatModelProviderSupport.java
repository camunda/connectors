/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetricsTokenUsageBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ChatModelProviderSupport {

  /**
   * TCP connect timeout applied to every chat model HTTP client. Kept short and constant — a
   * healthy public LLM endpoint accepts connections in well under a second, and anything in the
   * "seconds" range indicates DNS/firewall/proxy misconfiguration rather than model latency.
   * Matches the LangChain4j JDK client default so behavior stays consistent across providers.
   */
  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

  private ChatModelProviderSupport() {}

  public static Duration deriveTimeoutSetting(
      String timeoutType,
      ChatModelProperties chatModelConfig,
      @Nullable TimeoutConfiguration timeoutConfiguration,
      Logger logger) {
    var derivedTimeout =
        Optional.ofNullable(timeoutConfiguration)
            .map(TimeoutConfiguration::timeout)
            .filter(Duration::isPositive)
            .or(() -> Optional.of(chatModelConfig.api().defaultTimeout()))
            .get();

    if (logger.isDebugEnabled()) {
      logger.debug("Setting {} timeout to {}", timeoutType, derivedTimeout);
    }

    return derivedTimeout;
  }

  /**
   * Layers OpenAI cache/reasoning token detail onto an in-progress {@link
   * AgentMetricsTokenUsageBuilder}. Shared by every LangChain4J factory whose client returns {@link
   * OpenAiTokenUsage} (OpenAI and OpenAI-compatible today), so both get identical detail without
   * duplicating the mapping logic.
   */
  public static AgentMetricsTokenUsageBuilder applyOpenAiTokenUsageDetail(
      AgentMetricsTokenUsageBuilder builder, OpenAiTokenUsage usage) {
    Optional.ofNullable(usage.inputTokensDetails())
        .map(OpenAiTokenUsage.InputTokensDetails::cachedTokens)
        .ifPresent(cachedTokens -> builder.cacheReadTokenCount(nullToZero(cachedTokens)));
    Optional.ofNullable(usage.outputTokensDetails())
        .map(OpenAiTokenUsage.OutputTokensDetails::reasoningTokens)
        .ifPresent(reasoningTokens -> builder.reasoningTokenCount(nullToZero(reasoningTokens)));

    return builder;
  }

  private static int nullToZero(@Nullable Integer value) {
    return value != null ? value : 0;
  }
}
