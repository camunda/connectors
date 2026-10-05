/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the {@link ToolCallInspector}s to every tool call of a batch. A call answered by an
 * inspector becomes a local result, every other call remains for the process. The first {@link
 * ToolCallVerdict.Answer} of a call wins when several inspectors answer. The documents selected by
 * the answers are reported along with the results.
 */
public class LocalToolCallHandlerImpl implements LocalToolCallHandler {

  private final List<ToolCallInspector> inspectors;

  public LocalToolCallHandlerImpl(List<ToolCallInspector> inspectors) {
    this.inspectors = List.copyOf(inspectors);
  }

  @Override
  public LocalToolCallSplit handle(AgentConversation conversation, List<ToolCall> toolCalls) {
    if (inspectors.isEmpty() || toolCalls.isEmpty()) {
      return LocalToolCallSplit.allRemaining(toolCalls);
    }

    final var context = LocalToolCallContext.from(conversation);
    final var completedAt = OffsetDateTime.now();
    final var localResults = new ArrayList<ToolCallResult>();
    final var remainingCalls = new ArrayList<ToolCall>();
    final var returnDocumentIds = new ArrayList<String>();
    for (final var toolCall : toolCalls) {
      if (answer(toolCall, context) instanceof ToolCallVerdict.Answer answer) {
        localResults.add(
            ToolCallResult.builder()
                .id(toolCall.id())
                .name(toolCall.name())
                .content(answer.content())
                .completedAt(completedAt)
                .build());
        returnDocumentIds.addAll(answer.returnDocumentIds());
      } else {
        remainingCalls.add(toolCall);
      }
    }
    return new LocalToolCallSplit(localResults, remainingCalls, returnDocumentIds);
  }

  private ToolCallVerdict answer(ToolCall toolCall, LocalToolCallContext context) {
    for (final var inspector : inspectors) {
      final var verdict = inspector.inspect(toolCall, context);
      if (verdict instanceof ToolCallVerdict.Answer) {
        return verdict;
      }
    }
    return new ToolCallVerdict.Pass();
  }
}
