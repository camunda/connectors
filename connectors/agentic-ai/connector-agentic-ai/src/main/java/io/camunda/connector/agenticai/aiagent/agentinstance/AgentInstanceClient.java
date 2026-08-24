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
   * command is idempotent by {@code elementInstanceKey}. The instance's configuration (model,
   * provider, system prompt, tools) is sent as a {@code CONFIGURATION} history item rather than as
   * direct command fields, and {@code jobKey}/{@code jobLease} are forwarded to fence the batched
   * history against a superseded activation.
   *
   * @throws ConnectorException with code AGENT_INSTANCE_CREATION_FAILED when retries are exhausted
   *     or a non-retryable error occurs
   */
  AgentInstanceKey create(AgentExecutionContext agentExecutionContext);

  /**
   * Moves the agent instance to {@code TOOL_DISCOVERY}, fenced against a superseded job activation.
   * Silently skips when {@code agentInstanceKey} is {@code null}.
   *
   * @throws ConnectorException with code {@code AGENT_INSTANCE_UPDATE_FAILED} when retries are
   *     exhausted or a non-retryable error occurs
   * @throws ConnectorRetryException with code {@code AGENT_INSTANCE_SUPERSEDED} and zero retries
   *     when the job activation has been superseded
   */
  void applyToolDiscoveryStart(
      AgentExecutionContext executionContext, @Nullable AgentInstanceKey agentInstanceKey);

  /**
   * Records the start of a turn: moves the agent instance to {@code THINKING} and appends its input
   * messages (e.g. a user message, or tool call results correlated against {@code previousTurn}'s
   * tool calls) to the conversation history in a single batched, lease-fenced update. Also brings
   * the agent instance's recorded system prompt and tool list up to date whenever {@code
   * configuration} differs from what was in effect for {@code previousTurn}. Silently skips when
   * {@code agentInstanceKey} is {@code null} (e.g. agents that pre-date the agent-instance
   * feature).
   *
   * @throws ConnectorException with code {@code AGENT_INSTANCE_UPDATE_FAILED} when retries are
   *     exhausted or a non-retryable error occurs
   * @throws ConnectorRetryException with code {@code AGENT_INSTANCE_SUPERSEDED} and zero retries
   *     when the job activation has been superseded
   */
  void applyTurnStart(
      AgentExecutionContext executionContext,
      AgentConfiguration configuration,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentConversationTurn turn,
      Optional<AgentConversationTurn> previousTurn,
      OffsetDateTime turnIngestionTimestamp);

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
