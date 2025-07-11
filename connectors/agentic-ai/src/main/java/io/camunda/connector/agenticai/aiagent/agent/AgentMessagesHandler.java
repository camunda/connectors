/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

/**
 * Handles the addition of messages to the agent's memory, including:
 *
 * <ul>
 *   <li>system message
 *   <li>user messages
 *   <li>tool call results
 * </ul>
 *
 * Also handles mapping of tool call results in combination with gateway tool handlers.
 */
public interface AgentMessagesHandler {
  void addSystemMessage(
      AgentContext agentContext, RuntimeMemory memory, SystemPromptConfiguration systemPrompt);

  void addMessagesFromRequest(
      AgentContext agentContext,
      RuntimeMemory memory,
      UserPromptConfiguration userPrompt,
      List<ToolCallResult> toolCallResults);
}
