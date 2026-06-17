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
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Per-invocation input: pre-partitioned from the raw engine data. {@link #toolCallResults} holds
 * results with a non-null ID; {@link #eventMessages} holds results with a null ID (from
 * non-interrupting events).
 */
public final class AgentInvocationInput {

  private final @Nullable UserPromptConfiguration userPrompt;
  private final List<ToolCallResult> toolCallResults;
  private final List<ToolCallResult> eventMessages;

  private AgentInvocationInput(
      @Nullable UserPromptConfiguration userPrompt,
      List<ToolCallResult> toolCallResults,
      List<ToolCallResult> eventMessages) {
    this.userPrompt = userPrompt;
    this.toolCallResults = List.copyOf(toolCallResults);
    this.eventMessages = List.copyOf(eventMessages);
  }

  public static AgentInvocationInput from(
      @Nullable UserPromptConfiguration userPrompt, List<ToolCallResult> engineToolCallResults) {
    Objects.requireNonNull(engineToolCallResults, "engineToolCallResults must not be null");
    var partitioned =
        engineToolCallResults.stream().collect(Collectors.partitioningBy(r -> r.id() != null));
    return new AgentInvocationInput(userPrompt, partitioned.get(true), partitioned.get(false));
  }

  public @Nullable UserPromptConfiguration userPrompt() {
    return userPrompt;
  }

  public List<ToolCallResult> toolCallResults() {
    return toolCallResults;
  }

  public List<ToolCallResult> eventMessages() {
    return eventMessages;
  }
}
