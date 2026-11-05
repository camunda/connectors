/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the tool call results of local tool calls: executes the calls of runtime tools and
 * answers rejected calls with their rejection.
 */
public class LocalToolCallExecutor {

  private final RuntimeTools runtimeTools;

  public LocalToolCallExecutor(RuntimeTools runtimeTools) {
    this.runtimeTools = runtimeTools;
  }

  /**
   * Returns one entry per call.
   *
   * @throws IllegalStateException if a call is a process tool call
   */
  public List<ToolCallBatch.Entry> execute(
      AgentExecutionContext executionContext, AgentContext agentContext, List<ToolCall> calls) {
    final var now = OffsetDateTime.now(ZoneId.systemDefault());
    final var entries = new ArrayList<ToolCallBatch.Entry>();
    final var runtimeCalls = new ArrayList<ToolCall>();
    for (final var call : calls) {
      switch (call.resultSource()) {
        case RUNTIME -> runtimeCalls.add(call);
        case REJECTED ->
            entries.add(
                ToolCallBatch.Entry.from(ToolCallResultContent.forRejectedToolCall(call, now)));
        case PROCESS ->
            throw new IllegalStateException(
                "The call '%s' of the tool '%s' is a process tool call"
                    .formatted(call.id(), call.name()));
      }
    }
    if (!runtimeCalls.isEmpty()) {
      runtimeTools.execute(executionContext, agentContext, runtimeCalls).stream()
          .map(execution -> ToolCallBatch.Entry.from(execution.toolCallResult()))
          .forEach(entries::add);
    }
    return List.copyOf(entries);
  }
}
