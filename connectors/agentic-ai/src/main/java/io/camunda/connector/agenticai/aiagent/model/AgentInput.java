/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Per-invocation input: pre-partitioned from the raw engine data. {@link #toolCallResults} holds
 * results with a non-null ID; {@link #eventMessages} holds results with a null ID (from
 * non-interrupting events).
 */
public final class AgentInput {

  private final @Nullable UserPrompt userPrompt;
  private final List<ToolCallResult> toolCallResults;
  private final List<ToolCallResult> eventMessages;

  /**
   * Domain representation of the per-invocation user prompt, derived from the request-level {@link
   * UserPromptConfiguration} so the conversation turn composition does not depend on the request
   * DTO.
   */
  public record UserPrompt(@Nullable String prompt, List<Document> documents) {
    public UserPrompt {
      documents = documents == null ? List.of() : List.copyOf(documents);
    }
  }

  private AgentInput(
      @Nullable UserPrompt userPrompt,
      List<ToolCallResult> toolCallResults,
      List<ToolCallResult> eventMessages) {
    this.userPrompt = userPrompt;
    this.toolCallResults = List.copyOf(toolCallResults);
    this.eventMessages = List.copyOf(eventMessages);
  }

  public static AgentInput from(
      @Nullable UserPromptConfiguration userPrompt, List<ToolCallResult> engineToolCallResults) {
    Objects.requireNonNull(engineToolCallResults, "engineToolCallResults must not be null");
    var partitioned =
        engineToolCallResults.stream().collect(Collectors.partitioningBy(r -> r.id() != null));
    return new AgentInput(toUserPrompt(userPrompt), partitioned.get(true), partitioned.get(false));
  }

  private static @Nullable UserPrompt toUserPrompt(@Nullable UserPromptConfiguration userPrompt) {
    return userPrompt == null ? null : new UserPrompt(userPrompt.prompt(), userPrompt.documents());
  }

  public @Nullable UserPrompt userPrompt() {
    return userPrompt;
  }

  public List<ToolCallResult> toolCallResults() {
    return toolCallResults;
  }

  public List<ToolCallResult> eventMessages() {
    return eventMessages;
  }
}
