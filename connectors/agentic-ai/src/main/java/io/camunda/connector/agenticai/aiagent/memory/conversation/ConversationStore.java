/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * Responsible for storing and loading conversation records to external systems and loading them
 * into runtime memory.
 */
public interface ConversationStore {
  String type();

  /**
   * Execute agent logic within a session handler which can take care of optional transactional
   * behavior.
   */
  <T> T executeInSession(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler);

  /**
   * Entry point to compensate for failed job completion.
   *
   * <p>Note: this is not used in combination with an outbound connector, only when the agent is
   * handled by a job worker directly.
   */
  default void compensateFailedJobCompletion(
      AgentExecutionContext executionContext, AgentContext agentContext, Throwable failureReason) {}
}
