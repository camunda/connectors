/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Per-invocation input: pre-partitioned from the raw engine data. {@link #toolCallResults} holds
 * results with a non-null ID; {@link #eventMessages} holds results with a null ID (from
 * non-interrupting events).
 */
@NullMarked
public final class AgentInput {

  private final UserPrompt userPrompt;
  private final List<ToolCallResult> toolCallResults;
  private final List<ToolCallResult> eventMessages;

  /**
   * Domain representation of the per-invocation user prompt, derived from the request-level {@link
   * UserPromptConfiguration} so the conversation turn composition does not depend on the request
   * DTO.
   */
  public record UserPrompt(String prompt, List<Document> documents) {
    public UserPrompt {
      documents = List.copyOf(documents);
    }
  }

  private AgentInput(
      UserPrompt userPrompt,
      List<ToolCallResult> toolCallResults,
      List<ToolCallResult> eventMessages) {
    this.userPrompt = userPrompt;
    this.toolCallResults = List.copyOf(toolCallResults);
    this.eventMessages = List.copyOf(eventMessages);
  }

  public static AgentInput from(
      UserPromptConfiguration userPrompt, List<ToolCallResult> toolCallResults) {
    Objects.requireNonNull(toolCallResults, "toolCallResults must not be null");
    var partitioned =
        toolCallResults.stream().collect(Collectors.partitioningBy(r -> r.id() != null));
    return new AgentInput(
        new UserPrompt(
            userPrompt.prompt(),
            userPrompt.documents() == null ? List.of() : userPrompt.documents()),
        partitioned.getOrDefault(true, List.of()),
        partitioned.getOrDefault(false, List.of()));
  }

  public UserPrompt userPrompt() {
    return userPrompt;
  }

  public List<ToolCallResult> toolCallResults() {
    return toolCallResults;
  }

  public List<ToolCallResult> eventMessages() {
    return eventMessages;
  }
}
