/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import java.util.Objects;
import java.util.Optional;

public record AgentMetrics(int modelCalls, TokenUsage tokenUsage) {
  public static final AgentMetrics EMPTY = new AgentMetrics(0, TokenUsage.empty());

  public AgentMetrics {
    if (modelCalls < 0) {
      throw new IllegalArgumentException("Model calls must be non-negative");
    }

    Objects.requireNonNull(tokenUsage, "Token usage must not be null");
  }

  public AgentMetrics withModelCalls(int modelCalls) {
    return new AgentMetrics(modelCalls, tokenUsage);
  }

  public AgentMetrics incrementModelCalls(int additionalModelCalls) {
    return withModelCalls(modelCalls + additionalModelCalls);
  }

  public AgentMetrics withTokenUsage(TokenUsage tokenUsage) {
    return new AgentMetrics(modelCalls, tokenUsage);
  }

  public AgentMetrics incrementTokenUsage(TokenUsage additionalTokenUsage) {
    return withTokenUsage(tokenUsage.add(additionalTokenUsage));
  }

  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  public static AgentMetrics empty() {
    return EMPTY;
  }

  public record TokenUsage(int inputTokenCount, int outputTokenCount) {
    public static final TokenUsage EMPTY = new TokenUsage(0, 0);

    public int totalTokenCount() {
      return inputTokenCount + outputTokenCount;
    }

    public TokenUsage add(TokenUsage tokenUsage) {
      return new TokenUsage(
          inputTokenCount() + tokenUsage.inputTokenCount(),
          outputTokenCount() + tokenUsage.outputTokenCount());
    }

    public boolean isEmpty() {
      return this.equals(EMPTY);
    }

    public static TokenUsage empty() {
      return EMPTY;
    }

    public static TokenUsage from(dev.langchain4j.model.output.TokenUsage tokenUsage) {
      if (tokenUsage == null) {
        return empty();
      }

      return new TokenUsage(
          Optional.ofNullable(tokenUsage.inputTokenCount()).orElse(0),
          Optional.ofNullable(tokenUsage.outputTokenCount()).orElse(0));
    }
  }
}
