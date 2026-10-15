/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentMetrics.AgentMetricsJacksonProxyBuilder.class)
public record AgentMetrics(
    int modelCalls,
    @RecordBuilder.Initializer(source = TokenUsage.class, value = "empty") TokenUsage tokenUsage,
    int toolCalls,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Duration executionTime)
    implements AgentMetricsBuilder.With {

  public AgentMetrics {
    if (modelCalls < 0) {
      throw new IllegalArgumentException("Model calls must be non-negative");
    }
    Objects.requireNonNull(tokenUsage, "Token usage must not be null");
    if (toolCalls < 0) {
      throw new IllegalArgumentException("Tool calls must be non-negative");
    }
  }

  public AgentMetrics(int modelCalls, TokenUsage tokenUsage, int toolCalls) {
    this(modelCalls, tokenUsage, toolCalls, null);
  }

  /**
   * Adds the counter metrics (model calls, token usage, tool calls) of {@code other}. {@link
   * #executionTime} is a per-turn measurement and is intentionally not accumulated.
   */
  public AgentMetrics add(AgentMetrics other) {
    Objects.requireNonNull(other);

    return this.incrementModelCalls(other.modelCalls())
        .incrementTokenUsage(other.tokenUsage())
        .incrementToolCalls(other.toolCalls());
  }

  public AgentMetrics incrementModelCalls(int additionalModelCalls) {
    return withModelCalls(modelCalls + additionalModelCalls);
  }

  public AgentMetrics incrementTokenUsage(TokenUsage additionalTokenUsage) {
    return withTokenUsage(tokenUsage.add(additionalTokenUsage));
  }

  public AgentMetrics incrementToolCalls(int additionalToolCalls) {
    return withToolCalls(toolCalls + additionalToolCalls);
  }

  public static AgentMetrics empty() {
    return builder().build();
  }

  public static AgentMetricsBuilder builder() {
    return AgentMetricsBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentMetricsJacksonProxyBuilder extends AgentMetricsBuilder {}

  /**
   * Per-turn token consumption reported by the provider. {@code inputTokenCount} is always the
   * count of <b>new, non-cached</b> input tokens: {@code cacheReadTokenCount} and {@code
   * cacheCreationTokenCount} are separate, disjoint auxiliary buckets that are never also included
   * in {@code inputTokenCount} (Anthropic/Bedrock already report {@code input_tokens} excluding
   * cached tokens; the OpenAI path subtracts cached tokens back out so the semantics stay uniform
   * across providers). {@code reasoningTokenCount}, in contrast, is a breakdown <b>within</b>
   * {@code outputTokenCount} (reasoning is billed output) rather than a disjoint bucket — it is
   * informational only and is already included in {@code outputTokenCount}. There is intentionally
   * no combined total-token accessor: a single summed figure would be ambiguous about whether
   * cache/reasoning tokens are already included, so callers combine the fields they need directly.
   *
   * <ul>
   *   <li>{@code inputTokenCount} – new, non-cached input tokens.
   *   <li>{@code outputTokenCount} – all output tokens, including any reasoning tokens.
   *   <li>{@code cacheReadTokenCount} – input tokens served from a prompt cache; disjoint from
   *       {@code inputTokenCount}. Omitted from persisted JSON when zero.
   *   <li>{@code cacheCreationTokenCount} – input tokens written to a prompt cache; disjoint from
   *       {@code inputTokenCount}. Omitted from persisted JSON when zero.
   *   <li>{@code reasoningTokenCount} – the subset of {@code outputTokenCount} spent on reasoning;
   *       informational, not additive. Omitted from persisted JSON when zero.
   * </ul>
   */
  @AgenticAiRecord
  @JsonDeserialize(builder = TokenUsage.AgentMetricsTokenUsageJacksonProxyBuilder.class)
  public record TokenUsage(
      int inputTokenCount,
      int outputTokenCount,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) int cacheReadTokenCount,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) int cacheCreationTokenCount,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) int reasoningTokenCount)
      implements AgentMetricsTokenUsageBuilder.With {

    public TokenUsage(int inputTokenCount, int outputTokenCount) {
      this(inputTokenCount, outputTokenCount, 0, 0, 0);
    }

    public TokenUsage add(TokenUsage other) {
      return with(
          b ->
              b.inputTokenCount(b.inputTokenCount() + other.inputTokenCount())
                  .outputTokenCount(b.outputTokenCount() + other.outputTokenCount())
                  .cacheReadTokenCount(b.cacheReadTokenCount() + other.cacheReadTokenCount())
                  .cacheCreationTokenCount(
                      b.cacheCreationTokenCount() + other.cacheCreationTokenCount())
                  .reasoningTokenCount(b.reasoningTokenCount() + other.reasoningTokenCount()));
    }

    public static TokenUsage empty() {
      return builder().build();
    }

    public static AgentMetricsTokenUsageBuilder builder() {
      return AgentMetricsTokenUsageBuilder.builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class AgentMetricsTokenUsageJacksonProxyBuilder
        extends AgentMetricsTokenUsageBuilder {}
  }
}
