/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import java.util.List;

/**
 * Splits the tool calls of an assistant message into calls the agent answers itself and calls it
 * hands to the process.
 *
 * <p>Every call is decided on its own. A call of an available {@link RuntimeTool} is run by the
 * agent and gets its result immediately; a call of a process tool whose document parameter is not a
 * known document id is answered with an error result; every other call is left to the process. The
 * request handler then proceeds depending on what is left:
 *
 * <ul>
 *   <li>no call is left for the process: the runtime results become the input of another round of
 *       the same job and the model is called again;
 *   <li>calls are left for the process: only those are activated, the runtime results are kept in
 *       the agent context and merged with the process results when the next job composes its input,
 *       so the model sees one result per tool call without an extra model call. The runtime results
 *       are reported to the agent instance history by the job that answers them.
 * </ul>
 */
public interface RuntimeToolCallHandler {

  /**
   * The definitions of the runtime tools available for the agent execution; they are offered to the
   * model next to the tools of the process.
   */
  List<ToolDefinition> availableToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Splits {@code toolCalls}, the tool calls of the last assistant message of {@code conversation},
   * and runs the runtime ones in call order.
   */
  RuntimeToolCallSplit handle(
      AgentExecutionContext executionContext,
      AgentConversation conversation,
      List<ToolCall> toolCalls);

  /**
   * The outcome of splitting a batch.
   *
   * @param conversation the conversation with the context updates of the runtime results applied
   * @param runtimeResults the results of the calls answered by the agent, in the order of the calls
   * @param remainingCalls the calls that go to the process, in the order of the calls
   */
  record RuntimeToolCallSplit(
      AgentConversation conversation,
      List<ToolCallResultContent> runtimeResults,
      List<ToolCall> remainingCalls) {
    public RuntimeToolCallSplit {
      runtimeResults = List.copyOf(runtimeResults);
      remainingCalls = List.copyOf(remainingCalls);
    }

    /** A split in which every call goes to the process. */
    public static RuntimeToolCallSplit allRemaining(
        AgentConversation conversation, List<ToolCall> toolCalls) {
      return new RuntimeToolCallSplit(conversation, List.of(), toolCalls);
    }

    /** Returns {@code true} if the agent answered at least one call itself. */
    public boolean hasRuntimeResults() {
      return !runtimeResults.isEmpty();
    }

    /** Returns {@code true} if the agent answered every call (and there was at least one). */
    public boolean answersAllCalls() {
      return hasRuntimeResults() && remainingCalls.isEmpty();
    }
  }
}
