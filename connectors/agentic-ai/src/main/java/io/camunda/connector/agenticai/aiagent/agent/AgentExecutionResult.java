/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import org.springframework.lang.Nullable;

/**
 * Result of agent execution, containing the response and the conversation session for
 * post-completion lifecycle hooks.
 *
 * <p>Custom executors that manage their own storage return {@code AgentExecutionResult.of(response,
 * null)}. The handler only calls lifecycle hooks when {@code session} is non-null.
 */
public record AgentExecutionResult(
    @Nullable AgentResponse agentResponse, @Nullable ConversationSession session) {

  public static AgentExecutionResult noOp() {
    return new AgentExecutionResult(null, null);
  }

  public static AgentExecutionResult of(AgentResponse response, ConversationSession session) {
    return new AgentExecutionResult(response, session);
  }
}
