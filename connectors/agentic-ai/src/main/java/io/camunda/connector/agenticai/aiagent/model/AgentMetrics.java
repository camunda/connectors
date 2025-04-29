/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import java.util.Optional;

public record AgentMetrics(int modelCalls, TokenUsage tokenUsage) {
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

  public static AgentMetrics empty() {
    return new AgentMetrics(0, TokenUsage.empty());
  }

  public record TokenUsage(int inputTokenCount, int outputTokenCount) {

    public int totalTokenCount() {
      return inputTokenCount + outputTokenCount;
    }

    public TokenUsage add(TokenUsage tokenUsage) {
      return new TokenUsage(
          inputTokenCount() + tokenUsage.inputTokenCount(),
          outputTokenCount() + tokenUsage.outputTokenCount());
    }

    public static TokenUsage empty() {
      return new TokenUsage(0, 0);
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
