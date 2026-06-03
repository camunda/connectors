/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Objects;

@AgenticAiRecord
@JsonDeserialize(builder = AgentMetrics.AgentMetricsJacksonProxyBuilder.class)
public record AgentMetrics(
    int modelCalls,
    @RecordBuilder.Initializer(source = TokenUsage.class, value = "empty") TokenUsage tokenUsage,
    int toolCalls)
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

  public AgentMetrics incrementModelCalls(int additionalModelCalls) {
    return withModelCalls(modelCalls + additionalModelCalls);
  }

  public AgentMetrics incrementTokenUsage(TokenUsage additionalTokenUsage) {
    return withTokenUsage(tokenUsage.add(additionalTokenUsage));
  }

  public AgentMetrics incrementToolCalls(int additionalToolCalls) {
    return withToolCalls(toolCalls + additionalToolCalls);
  }

  public AgentMetrics minus(AgentMetrics other) {
    return builder()
        .modelCalls(requirePositive(modelCalls - other.modelCalls(), "modelCalls"))
        .tokenUsage(tokenUsage.minus(other.tokenUsage()))
        .toolCalls(requirePositive(toolCalls - other.toolCalls(), "toolCalls"))
        .build();
  }

  public static AgentMetrics empty() {
    return builder().build();
  }

  public static AgentMetricsBuilder builder() {
    return AgentMetricsBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentMetricsJacksonProxyBuilder extends AgentMetricsBuilder {}

  @AgenticAiRecord
  @JsonDeserialize(builder = TokenUsage.AgentMetricsTokenUsageJacksonProxyBuilder.class)
  public record TokenUsage(int inputTokenCount, int outputTokenCount)
      implements AgentMetricsTokenUsageBuilder.With {

    public int totalTokenCount() {
      return inputTokenCount + outputTokenCount;
    }

    public TokenUsage add(TokenUsage tokenUsage) {
      return with(
          builder ->
              builder
                  .inputTokenCount(builder.inputTokenCount() + tokenUsage.inputTokenCount())
                  .outputTokenCount(builder.outputTokenCount() + tokenUsage.outputTokenCount()));
    }

    public TokenUsage minus(TokenUsage other) {
      return with(
          builder ->
              builder
                  .inputTokenCount(
                      requirePositive(
                          builder.inputTokenCount() - other.inputTokenCount(), "inputTokenCount"))
                  .outputTokenCount(
                      requirePositive(
                          builder.outputTokenCount() - other.outputTokenCount(),
                          "outputTokenCount")));
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

  private static int requirePositive(int value, String field) {
    if (value < 0) {
      var targetMessage =
          "%s value is negative after subtraction. Actual value: %s".formatted(field, value);
      throw new IllegalStateException(targetMessage);
    }
    return value;
  }
}
