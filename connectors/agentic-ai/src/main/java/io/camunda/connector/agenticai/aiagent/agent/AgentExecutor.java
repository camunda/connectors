/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

/**
 * Executes the agent logic for a single iteration.
 *
 * <p>Receives the initialized context and tool call results, returns an {@link
 * AgentExecutionResult} containing the {@link
 * io.camunda.connector.agenticai.aiagent.model.AgentResponse} and a {@link
 * io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession} for lifecycle
 * hooks.
 *
 * <p>The executor is responsible for:
 *
 * <ul>
 *   <li>Loading/storing conversation from the {@link
 *       io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore}
 *   <li>Managing the runtime memory (context window)
 *   <li>Constructing messages (system, user, tool results)
 *   <li>Invoking the LLM via {@link
 *       io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter}
 *   <li>Handling gateway tool call transformations
 * </ul>
 *
 * <p>The executor is NOT responsible for:
 *
 * <ul>
 *   <li>Agent initialization ({@link AgentInitializer} handles this)
 *   <li>Job completion (the caller handles this)
 *   <li>Job-level error handling (the caller handles this)
 *   <li>Conversation session lifecycle hooks (the caller handles this via the returned session)
 * </ul>
 */
public interface AgentExecutor {
  AgentExecutionResult execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults);
}
