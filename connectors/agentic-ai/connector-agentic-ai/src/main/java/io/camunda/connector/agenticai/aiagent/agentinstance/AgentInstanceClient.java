/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import java.time.OffsetDateTime;
import java.util.List;
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
   * @param turnIngestionTimestamp the {@code producedAt} for non-tool-result items (e.g. a user
   *     message); tool-result items use their own resolved completion timestamp instead (ADR 008)
   * @throws ConnectorException with code AGENT_INSTANCE_HISTORY_ITEM_FAILED when retries are
   *     exhausted or a non-retryable error occurs
   */
  void createHistoryForInputMessages(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      Optional<AgentConversationTurn> previousTurn,
      OffsetDateTime turnIngestionTimestamp);

  /**
   * Appends the assistant history item including turn metrics for the given completed turn, after
   * the LLM call. Silently skips when {@code agentInstanceKey} is {@code null}.
   *
   * @param producedAt the {@code producedAt} for the assistant history item
   * @throws ConnectorException with code AGENT_INSTANCE_HISTORY_ITEM_FAILED when retries are
   *     exhausted or a non-retryable error occurs
   */
  void createHistoryForAssistantMessage(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      OffsetDateTime producedAt);

  /**
   * Appends one {@code TOOL_RESULT} history item per result, for a turn whose tool-call batch is
   * not yet complete. Skips silently when {@code agentInstanceKey} is {@code null}.
   *
   * <p>Each result's id must correspond to a tool call in {@code previousTurn}; a non-matching id
   * fails. {@link #createHistoryForInputMessages} writes the same result again once the batch
   * completes.
   *
   * @param previousTurn supplies the correlating tool calls and a best-effort iteration key ({@code
   *     previousTurn.iterationKey() + 1}); the batch write's key is authoritative
   * @throws ConnectorException with code AGENT_INSTANCE_HISTORY_ITEM_FAILED when retries are
   *     exhausted or a non-retryable error occurs
   */
  void createHistoryForToolCallResults(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      List<ToolCallResult> toolCallResults,
      AgentConversationTurn previousTurn);

  /**
   * Records the start of a turn: moves the agent instance to {@code THINKING} and appends its input
   * messages (e.g. a user message, or tool call results correlated against {@code previousTurn}'s
   * tool calls) to the conversation history, in the same way as {@link
   * #createHistoryForInputMessages}. Also brings the agent instance's recorded system prompt and
   * tool list up to date whenever {@code configuration} differs from what was in effect for {@code
   * previousTurn}. Silently skips when {@code agentInstanceKey} is {@code null} (e.g. agents that
   * pre-date the agent-instance feature).
   *
   * @throws ConnectorException with code {@code AGENT_INSTANCE_UPDATE_FAILED} when retries are
   *     exhausted or a non-retryable error occurs
   * @throws ConnectorRetryException with code {@code AGENT_INSTANCE_SUPERSEDED} and zero retries
   *     when the job activation has been superseded
   */
  void applyTurnStart(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      Optional<AgentConversationTurn> previousTurn,
      OffsetDateTime turnIngestionTimestamp,
      AgentConfiguration configuration);

  /**
   * Records the completion of a turn: sets the agent instance's status to {@code status} and
   * appends the assistant's response for {@code turn} — including that turn's token, model-call and
   * tool-call metrics — to the conversation history. Silently skips when {@code agentInstanceKey}
   * is {@code null}.
   *
   * @param producedAt when the assistant response was produced
   * @throws ConnectorException with code {@code AGENT_INSTANCE_UPDATE_FAILED} when retries are
   *     exhausted or a non-retryable error occurs
   * @throws ConnectorRetryException with code {@code AGENT_INSTANCE_SUPERSEDED} and zero retries
   *     when the job activation has been superseded
   */
  void applyTurnCompletion(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      OffsetDateTime producedAt,
      AgentInstanceUpdateStatus status);

  /**
   * Appends one tool call result per entry in {@code toolCallResults} to the conversation history,
   * without changing the agent instance's status. Silently skips when {@code agentInstanceKey} is
   * {@code null}.
   *
   * @throws ConnectorException with code {@code AGENT_INSTANCE_UPDATE_FAILED} when retries are
   *     exhausted or a non-retryable error occurs
   * @throws ConnectorRetryException with code {@code AGENT_INSTANCE_SUPERSEDED} and zero retries
   *     when the job activation has been superseded
   */
  void applyToolCallResults(
      AgentExecutionContext executionContext,
      @Nullable AgentInstanceKey agentInstanceKey,
      List<ToolCallResult> toolCallResults,
      AgentConversationTurn previousTurn);
}
