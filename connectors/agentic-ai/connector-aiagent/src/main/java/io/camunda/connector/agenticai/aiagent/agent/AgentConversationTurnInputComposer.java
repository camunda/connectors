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
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import java.util.List;

/**
 * Composes the next conversation turn input from raw history and the current invocation state. The
 * composer produces no tool call result itself: the results of the local tool calls are given to
 * it.
 */
public interface AgentConversationTurnInputComposer {

  /**
   * Composes the next turn input. Only the results of the process tool calls of the previous turn
   * are resolved: they are either all there (or the missing ones cancelled by an interrupting
   * event), which completes the process side of the turn, or the result is {@link
   * CompositionResult.Deferred}.
   *
   * <p>A previous turn without local tool calls is composed in this one step. When the previous
   * turn also has local tool calls and the process side is complete, the result is {@link
   * CompositionResult.LocalToolCallResultsRequired}: get the results of the calls and compose again
   * with {@link #composeWithLocalToolCallResults}.
   */
  CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput);

  /**
   * Composes the next turn input with the results of the local tool calls of the previous turn. The
   * tool call results of the turn are the results of the process and the results of the agent tool
   * calls, in call order. The result is never {@link
   * CompositionResult.LocalToolCallResultsRequired}.
   *
   * @param executions the results of the local tool calls of the last assistant message of {@code
   *     previousConversation}, none for a turn without such calls
   */
  CompositionResult composeWithLocalToolCallResults(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      List<RuntimeToolExecution> executions);
}
