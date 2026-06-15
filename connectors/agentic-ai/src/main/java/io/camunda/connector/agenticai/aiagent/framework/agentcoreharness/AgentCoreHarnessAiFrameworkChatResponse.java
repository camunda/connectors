/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.AssistantMessage;

/**
 * Response from the AgentCore Harness framework adapter.
 *
 * @param agentContext the updated agent context with metrics
 * @param assistantMessage the assistant message containing text and/or tool calls
 * @param runtimeSessionId the Harness session ID for conversation continuity
 */
public record AgentCoreHarnessAiFrameworkChatResponse(
    AgentContext agentContext, AssistantMessage assistantMessage, String runtimeSessionId)
    implements AiFrameworkChatResponse<String> {

  @Override
  public String rawChatResponse() {
    return runtimeSessionId;
  }
}
