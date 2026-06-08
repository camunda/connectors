/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Per-invocation input: the user's initial message (first turn) and/or engine tool call results
 * (subsequent turns). Both are externally sourced and distinct from static configuration.
 */
public record AgentInvocationInput(
    @Nullable UserPromptConfiguration userPrompt, List<ToolCallResult> engineToolCallResults) {

  public AgentInvocationInput {
    Objects.requireNonNull(engineToolCallResults, "engineToolCallResults must not be null");
    engineToolCallResults = List.copyOf(engineToolCallResults);
  }

  public static AgentInvocationInput from(AgentExecutionContext executionContext) {
    return new AgentInvocationInput(
        executionContext.userPrompt(),
        executionContext.initialToolCallResults() != null
            ? executionContext.initialToolCallResults()
            : List.of());
  }
}
