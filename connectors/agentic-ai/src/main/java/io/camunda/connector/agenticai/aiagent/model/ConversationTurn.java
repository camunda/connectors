/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One LLM invocation: the messages that drove it, the response, and per-turn metrics. iterationKey
 * is 1-based and counts LLM calls across the entire agent lifecycle.
 *
 * <p>A turn is <em>pending</em> when {@code assistantMessage} is {@code null} — created by {@link
 * AgentConversation#addNextTurn} before the LLM call — and <em>complete</em> after {@link
 * AgentConversation#ingest}.
 */
public record ConversationTurn(
    int iterationKey,
    List<Message> inputMessages,
    @Nullable AssistantMessage assistantMessage,
    AgentMetrics metrics) {

  public ConversationTurn {
    Objects.requireNonNull(inputMessages, "inputMessages must not be null");
    Objects.requireNonNull(metrics, "metrics must not be null");
    inputMessages = List.copyOf(inputMessages);
  }

  public ConversationTurn withAssistantMessage(
      AssistantMessage assistantMessage, AgentMetrics metrics) {
    return new ConversationTurn(iterationKey, inputMessages, assistantMessage, metrics);
  }

  public boolean hasToolCalls() {
    return assistantMessage != null && assistantMessage.hasToolCalls();
  }

  /** Returns {@code true} if any tool call result in this turn's input was interrupted. */
  public boolean hasInterruptedToolCallResults() {
    return inputMessages.stream()
        .filter(ToolCallResultMessage.class::isInstance)
        .map(ToolCallResultMessage.class::cast)
        .flatMap(msg -> msg.results().stream())
        .anyMatch(
            result ->
                Boolean.TRUE.equals(
                    result.properties().getOrDefault(ToolCallResult.PROPERTY_INTERRUPTED, false)));
  }
}
