/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.Objects;

@AgenticAiRecordBuilder
public record AgentMetrics(int modelCalls, TokenUsage tokenUsage)
    implements AgentMetricsBuilder.With {
  public static final AgentMetrics EMPTY =
      AgentMetricsBuilder.builder().tokenUsage(TokenUsage.empty()).build();

  public AgentMetrics {
    if (modelCalls < 0) {
      throw new IllegalArgumentException("Model calls must be non-negative");
    }

    Objects.requireNonNull(tokenUsage, "Token usage must not be null");
  }

  public AgentMetrics incrementModelCalls(int additionalModelCalls) {
    return withModelCalls(modelCalls + additionalModelCalls);
  }

  public AgentMetrics incrementTokenUsage(TokenUsage additionalTokenUsage) {
    return withTokenUsage(tokenUsage.add(additionalTokenUsage));
  }

  @JsonIgnore
  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  public static AgentMetrics empty() {
    return EMPTY;
  }

  public static AgentMetricsBuilder builder() {
    return AgentMetricsBuilder.builder();
  }

  @AgenticAiRecordBuilder
  public record TokenUsage(int inputTokenCount, int outputTokenCount)
      implements AgentMetricsTokenUsageBuilder.With {
    public static final TokenUsage EMPTY = AgentMetricsTokenUsageBuilder.builder().build();

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

    public boolean isEmpty() {
      return this.equals(EMPTY);
    }

    public static TokenUsage empty() {
      return EMPTY;
    }

    public static AgentMetricsTokenUsageBuilder builder() {
      return AgentMetricsTokenUsageBuilder.builder();
    }
  }
}
