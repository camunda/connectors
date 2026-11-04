/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds a {@link ToolCallSummary} for each completed turn of a conversation. */
public class ToolCallSummaryService {

  public List<ToolCallSummary> summarize(AgentConversation conversation) {
    final var summaries = new ArrayList<ToolCallSummary>();
    for (var turn : conversation.turns()) {
      appendSummary(summaries, turn);
    }
    return summaries;
  }

  public void appendSummary(List<ToolCallSummary> summaries, AgentConversationTurn turn) {
    final var assistantMessage = turn.assistantMessage();
    if (assistantMessage == null) {
      return;
    }

    final var toolCalls =
        assistantMessage.toolCalls() == null ? List.<ToolCall>of() : assistantMessage.toolCalls();
    final Map<String, Long> callsPerTool = new LinkedHashMap<>();
    for (var toolCall : toolCalls) {
      callsPerTool.merge(toolCall.name(), 1L, Long::sum);
    }

    final var firstContent =
        assistantMessage.content() == null || assistantMessage.content().isEmpty()
            ? null
            : assistantMessage.content().getFirst();

    summaries.add(
        new ToolCallSummary(
            turn.iterationKey(),
            toolCalls.size(),
            callsPerTool,
            toolCalls.isEmpty() ? Optional.empty() : Optional.of(toolCalls.getFirst().name()),
            firstContent == null ? "none" : responseKind(firstContent)));
  }

  private String responseKind(Content content) {
    if (content instanceof TextContent) {
      return "text";
    } else if (content instanceof DocumentContent) {
      return "document";
    } else if (content instanceof ObjectContent) {
      return "object";
    }
    return "other";
  }
}
