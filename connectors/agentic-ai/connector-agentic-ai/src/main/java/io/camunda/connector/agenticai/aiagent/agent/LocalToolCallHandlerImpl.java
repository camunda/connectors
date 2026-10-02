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
import java.util.Optional;

/**
 * Applies the {@link ToolCallInspector}s to every tool call of a batch. When at least one call is
 * answered by an inspector, every call of the batch is answered here: answered calls get their
 * answer, calls that passed get a "not executed" result.
 */
public class LocalToolCallHandlerImpl implements LocalToolCallHandler {

  static final String NOT_EXECUTED =
      "Not executed because another tool call in the same batch failed. "
          + "Resend this call separately or after fixing the failed call.";

  private final List<ToolCallInspector> inspectors;

  public LocalToolCallHandlerImpl(List<ToolCallInspector> inspectors) {
    this.inspectors = List.copyOf(inspectors);
  }

  @Override
  public Optional<List<ToolCallResult>> handle(
      AgentConversation conversation, List<ToolCall> toolCalls) {
    if (inspectors.isEmpty() || toolCalls.isEmpty()) {
      return Optional.empty();
    }

    final var context = LocalToolCallContext.from(conversation);
    final var verdicts = toolCalls.stream().map(toolCall -> inspect(toolCall, context)).toList();
    if (verdicts.stream().allMatch(ToolCallVerdict.Pass.class::isInstance)) {
      return Optional.empty();
    }

    final var completedAt = OffsetDateTime.now();
    final var results = new ArrayList<ToolCallResult>(toolCalls.size());
    for (int i = 0; i < toolCalls.size(); i++) {
      final var toolCall = toolCalls.get(i);
      final Object content =
          verdicts.get(i) instanceof ToolCallVerdict.Answer answer
              ? answer.content()
              : NOT_EXECUTED;
      results.add(
          ToolCallResult.builder()
              .id(toolCall.id())
              .name(toolCall.name())
              .content(content)
              .completedAt(completedAt)
              .build());
    }
    return Optional.of(results);
  }

  private ToolCallVerdict inspect(ToolCall toolCall, LocalToolCallContext context) {
    for (final var inspector : inspectors) {
      final var verdict = inspector.inspect(toolCall, context);
      if (verdict instanceof ToolCallVerdict.Answer) {
        return verdict;
      }
    }
    return new ToolCallVerdict.Pass();
  }
}
