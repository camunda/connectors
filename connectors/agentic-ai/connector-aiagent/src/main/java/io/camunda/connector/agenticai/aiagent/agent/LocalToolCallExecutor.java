/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
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
   * @throws IllegalArgumentException if the result of a runtime tool breaks the rules of its
   *     documents, a defect of the tool
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
        .map(execution ->
            checked(
                entryOf(execution, agentContext.documentHandling()),
                agentContext.documentHandling()))
        .toList();
  }

  private static ToolCallBatch.Entry checked(
      ToolCallBatch.Entry entry, DocumentHandling documentHandling) {
    switch (documentHandling) {
      case DocumentHandling.Tracked tracked -> entry.requireWellFormed(tracked.documents());
      case DocumentHandling.Untracked ignored -> entry.requireNoDocumentIds();
    }
    return entry;
  }

  /**
   * The entry of an execution with the effects of a successful result. The documents are the ones
   * the model reads as handles for an agent instance that tracks its documents, and the ones the
   * generic walker finds for one that does not.
   */
  private ToolCallBatch.Entry entryOf(
      RuntimeToolExecution execution, DocumentHandling documentHandling) {
    final var result = execution.toolCallResult();
    final List<ToolCallEffect> effects =
        switch (execution.result()) {
          case RuntimeToolResult.Success success -> success.effects();
          case RuntimeToolResult.Error ignored -> List.of();
        };
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored ->
          ToolCallBatch.Entry.withRenderedDocuments(result, objectHandleRenderer, effects);
      case DocumentHandling.Untracked ignored -> ToolCallBatch.Entry.from(result, effects);
    };
  }

  private static boolean isExecutedByTheAgent(ToolCall call) {
    return switch (call.resultSource()) {
      case RUNTIME -> true;
      case PROCESS, REJECTED -> false;
    };
  }
}
