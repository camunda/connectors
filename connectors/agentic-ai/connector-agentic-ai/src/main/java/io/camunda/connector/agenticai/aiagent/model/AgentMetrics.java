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
   * Token usage for a model interaction.
   *
   * <p>Aggregation semantics: {@code cacheReadTokenCount} and {@code cacheCreationTokenCount} are
   * subsets already counted within {@code inputTokenCount}; {@code reasoningTokenCount} is a subset
   * already counted within {@code outputTokenCount}. {@link #totalTokenCount()} is therefore {@code
   * input + output} and never double-counts. Populated per-round by the native provider
   * implementations.
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

    public int totalTokenCount() {
      return inputTokenCount + outputTokenCount;
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
