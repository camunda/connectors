/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import java.util.List;

/**
 * Produces the tool call results of the local tool calls that are executed: the calls of runtime
 * tools. A rejected call is answered by its rejection when its batch is created (see {@link
 * ToolCallBatch#of}), so it never reaches this step.
 */
public class LocalToolCallExecutor {

  private final RuntimeTools runtimeTools;
  private final ObjectHandleRenderer objectHandleRenderer;

  public LocalToolCallExecutor(
      RuntimeTools runtimeTools, ObjectHandleRenderer objectHandleRenderer) {
    this.runtimeTools = runtimeTools;
    this.objectHandleRenderer = objectHandleRenderer;
  }

  /**
   * Returns one entry per call. An agent instance that tracks its documents registers the documents
   * of a result that the model reads as handles, also those inside a typed record.
   *
   * @throws IllegalStateException if a call is not a call of a runtime tool
   */
  public List<ToolCallBatch.Entry> execute(
      AgentExecutionContext executionContext, AgentContext agentContext, List<ToolCall> calls) {
    for (final var call : calls) {
      if (!isExecutedByTheAgent(call)) {
        throw new IllegalStateException(
            "The call '%s' of the tool '%s' is not executed by the agent, its result source is %s"
                .formatted(call.id(), call.name(), call.resultSource()));
      }
    }
    if (calls.isEmpty()) {
      return List.of();
    }
    return runtimeTools.execute(executionContext, agentContext, calls).stream()
        .map(execution -> entry(execution.toolCallResult(), agentContext.documentHandling()))
        .toList();
  }

  private ToolCallBatch.Entry entry(
      ToolCallResultContent result, DocumentHandling documentHandling) {
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored ->
          ToolCallBatch.Entry.withRenderedDocuments(result, objectHandleRenderer);
      case DocumentHandling.Untracked ignored -> ToolCallBatch.Entry.from(result);
    };
  }

  private static boolean isExecutedByTheAgent(ToolCall call) {
    return switch (call.resultSource()) {
      case RUNTIME -> true;
      case PROCESS, REJECTED -> false;
    };
  }
}
