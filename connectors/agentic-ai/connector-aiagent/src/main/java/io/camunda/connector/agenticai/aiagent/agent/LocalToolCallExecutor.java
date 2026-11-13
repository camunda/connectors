/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
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
   * @throws
   *     io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException
   *     if an agent instance that tracks its documents gets a document that is not a Camunda
   *     document from a runtime tool
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
    final var executions = runtimeTools.execute(executionContext, agentContext, calls);
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Tracked tracked ->
          executions.stream()
              .map(execution -> trackedEntry(execution, tracked.documents()))
              .toList();
      case DocumentHandling.Untracked ignored ->
          executions.stream().map(LocalToolCallExecutor::untrackedEntry).toList();
    };
  }

  /**
   * The entry of an execution for an agent instance that tracks its documents, with the effects of
   * a successful result. The documents are the ones the model reads as handles.
   */
  private ToolCallBatch.Entry trackedEntry(RuntimeToolExecution execution, AgentDocuments known) {
    final var entry =
        ToolCallBatch.Entry.withRenderedDocuments(
            execution.toolCallResult(), objectHandleRenderer, effectsOf(execution));
    entry.requireWellFormed(known);
    return entry;
  }

  /**
   * The entry of an execution for an agent instance that does not track its documents. Such an
   * instance gives no document an id, so a result asks nothing of the agent; the documents are the
   * ones the generic walker finds.
   */
  private static ToolCallBatch.Entry untrackedEntry(RuntimeToolExecution execution) {
    if (!effectsOf(execution).isEmpty()) {
      throw new IllegalArgumentException(
          "The runtime tool '%s' refers to a document by its id, which an agent instance that does not track its documents does not support"
              .formatted(execution.call().name()));
    }
    return ToolCallBatch.Entry.from(execution.toolCallResult());
  }

  private static List<ToolCallEffect> effectsOf(RuntimeToolExecution execution) {
    return switch (execution.result()) {
      case RuntimeToolResult.Success success -> success.effects();
      case RuntimeToolResult.Error ignored -> List.of();
    };
  }

  private static boolean isExecutedByTheAgent(ToolCall call) {
    return switch (call.resultSource()) {
      case RUNTIME -> true;
      case PROCESS, REJECTED -> false;
    };
  }
}
