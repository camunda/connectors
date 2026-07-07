/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface AgentInstanceClient {

  /**
   * Creates an agent instance on the engine, or returns the key of the existing one. The engine
   * command is idempotent by {@code elementInstanceKey}.
   *
   * @throws ConnectorException with code AGENT_INSTANCE_CREATION_FAILED when retries are exhausted
   *     or a non-retryable error occurs
   */
  AgentInstanceKey create(AgentExecutionContext agentExecutionContext);

  /**
   * Updates the status and/or metrics of an existing agent instance. Silently skips when {@code
   * agentInstanceKey} is {@code null} (e.g. agents that pre-date this feature).
   *
   * @throws ConnectorException with code AGENT_INSTANCE_UPDATE_FAILED when retries are exhausted or
   *     a non-retryable error occurs
   */
  void update(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentInstanceUpdateRequest request);

  /**
   * Appends one conversation history item per input message of the given turn before the LLM call.
   * All input messages are considered, e.g. user messages, including virtual ones as well as tool
   * call results. Silently skips when {@code agentInstanceKey} is {@code null} (e.g. agents that
   * pre-date the agent-instance feature).
   *
   * <p>{@code previousTurn} is the turn preceding {@code turn} (typically {@code
   * conversation.lastTurn()}, which is the previous turn while the current turn is still pending);
   * its assistant tool calls supply the originating arguments populated on tool-result history
   * items, correlated by tool-call id. A tool-call result with a non-null id that has no
   * originating tool call in {@code previousTurn} is treated as an invariant violation.
   *
   * @throws ConnectorException with code AGENT_INSTANCE_HISTORY_ITEM_FAILED when retries are
   *     exhausted or a non-retryable error occurs
   */
  void createHistoryForInputMessages(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      Optional<AgentConversationTurn> previousTurn);

  /**
   * Appends the assistant history item including turn metrics for the given completed turn, after
   * the LLM call. Silently skips when {@code agentInstanceKey} is {@code null}.
   *
   * @throws ConnectorException with code AGENT_INSTANCE_HISTORY_ITEM_FAILED when retries are
   *     exhausted or a non-retryable error occurs
   */
  void createHistoryForAssistantMessage(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn);
}
