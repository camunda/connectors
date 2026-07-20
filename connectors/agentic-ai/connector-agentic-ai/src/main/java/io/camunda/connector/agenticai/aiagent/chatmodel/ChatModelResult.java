/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;

/**
 * The outcome of a single round-trip performed by a {@link ChatModelApi}: the assistant message
 * produced for the round and its metrics ({@code modelCalls}, {@code tokenUsage}, {@code
 * toolCalls}, and the measured {@code executionTime}).
 */
public sealed interface ChatModelResult {

  AssistantMessage assistantMessage();

  AgentMetrics metrics();

  /** The round-trip finished the agent's turn; no further model call is needed for it. */
  record Completed(AssistantMessage assistantMessage, AgentMetrics metrics)
      implements ChatModelResult {}

  /**
   * The provider paused mid-turn (e.g. Anthropic's {@code pause_turn} stop reason) and must be
   * called again — without new user/tool input — to continue producing the same logical turn.
   */
  record Continuation(AssistantMessage assistantMessage, AgentMetrics metrics)
      implements ChatModelResult {}
}
