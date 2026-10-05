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
import java.util.List;

/**
 * Splits the tool calls of an assistant message into calls the agent answers itself and calls it
 * hands to the process.
 *
 * <p>Every call is decided on its own: a call answered by a {@link ToolCallInspector} (a built-in
 * tool, an invalid call) gets its result immediately, every other call is left to the process. The
 * request handler then proceeds depending on what is left:
 *
 * <ul>
 *   <li>no call is left for the process: the local results become the input of another round of the
 *       same job and the model is called again;
 *   <li>calls are left for the process: only those are activated, the local results are kept in the
 *       agent context and merged with the process results when the next job composes its input, so
 *       the model sees one result per tool call without an extra model call.
 * </ul>
 *
 * <p>A new locally handled tool plugs in by implementing a {@link ToolCallInspector} that answers
 * its calls with {@link ToolCallVerdict.Answer} and registering it as a bean; neither the handler
 * nor the loop change.
 */
public interface LocalToolCallHandler {

  /**
   * Splits {@code toolCalls}, the tool calls of the last assistant message of {@code conversation}.
   */
  LocalToolCallSplit handle(AgentConversation conversation, List<ToolCall> toolCalls);

  /**
   * The outcome of splitting a batch.
   *
   * @param localResults the results of the calls answered by the agent, in the order of the calls
   * @param remainingCalls the calls that go to the process, in the order of the calls
   * @param returnDocumentIds the documents the local answers select for the final response of the
   *     agent
   */
  record LocalToolCallSplit(
      List<ToolCallResult> localResults,
      List<ToolCall> remainingCalls,
      List<String> returnDocumentIds) {
    public LocalToolCallSplit {
      localResults = List.copyOf(localResults);
      remainingCalls = List.copyOf(remainingCalls);
      returnDocumentIds = List.copyOf(returnDocumentIds);
    }

    /** A split in which every call goes to the process. */
    public static LocalToolCallSplit allRemaining(List<ToolCall> toolCalls) {
      return new LocalToolCallSplit(List.of(), toolCalls, List.of());
    }

    /** Returns {@code true} if the agent answered at least one call itself. */
    public boolean hasLocalResults() {
      return !localResults.isEmpty();
    }

    /** Returns {@code true} if the agent answered every call (and there was at least one). */
    public boolean answersAllCalls() {
      return hasLocalResults() && remainingCalls.isEmpty();
    }
  }
}
