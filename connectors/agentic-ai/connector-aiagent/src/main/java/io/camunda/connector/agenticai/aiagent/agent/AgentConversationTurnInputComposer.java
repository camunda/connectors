/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.List;

/**
 * Composes the next conversation turn input from raw history and the current invocation state. The
 * composer runs no tool: the results of runtime tool calls are given to it.
 */
public interface AgentConversationTurnInputComposer {

  /**
   * Composes the next turn input. Only the results of the process tool calls of the previous turn
   * are resolved: they are either all there (or the missing ones cancelled by an interrupting
   * event), which completes the process side of the turn, or the result is {@link
   * CompositionResult.Deferred}.
   *
   * <p>A previous turn without runtime tool calls is composed in this one step. When the previous
   * turn also has runtime tool calls and the process side is complete, the result is {@link
   * CompositionResult.RuntimeToolResultsRequired}: run the calls and compose again with {@link
   * #composeWithRuntimeToolResults}.
   */
  CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput);

  /**
   * Composes the next turn input with the executions of the runtime tool calls of the previous
   * turn. The tool call results of the turn are the results of the process and of the runtime
   * tools, in call order. The result is never {@link CompositionResult.RuntimeToolResultsRequired}.
   *
   * <p>The executions can ask for changes of the agent: the {@link CompositionResult.NextTurn}
   * holds the {@link CompositionResult.NextTurn#agentContext() agent context} with the response
   * documents they selected, and the caller persists that context. The documents the executions
   * returned are not in it: they are part of the {@link CompositionResult.NextTurn#documents()
   * documents of the turn}, and the caller registers them in the context it persists.
   *
   * @param agentContext the agent context the executions ran with
   * @param executions the executions of the runtime tool calls of the last assistant message of
   *     {@code previousConversation}, none for a turn without such calls
   */
  CompositionResult composeWithRuntimeToolResults(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      List<RuntimeToolExecution> executions);
}
