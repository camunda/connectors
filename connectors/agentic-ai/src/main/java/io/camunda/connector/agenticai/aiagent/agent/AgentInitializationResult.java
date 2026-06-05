/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

public sealed interface AgentInitializationResult
    permits AgentInitializationResult.ReadyToConverse,
        AgentInitializationResult.DiscoverTools,
        AgentInitializationResult.DeferConversation {

  /** Agent provisioned and tools resolved: proceed to the conversation phase. */
  record ReadyToConverse(AgentContext agentContext, List<ToolCallResult> engineToolCallResults)
      implements AgentInitializationResult {}

  /** Gateway tools require discovery: dispatch these discovery tool calls, then await results. */
  record DiscoverTools(AgentContext agentContext, List<ToolCall> toolDiscoveryToolCalls)
      implements AgentInitializationResult {}

  /** Discovery already dispatched; results not all present yet — no-op this turn. */
  record DeferConversation() implements AgentInitializationResult {}
}
