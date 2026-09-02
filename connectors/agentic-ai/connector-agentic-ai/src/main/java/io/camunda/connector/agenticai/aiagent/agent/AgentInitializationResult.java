/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface AgentInitializationResult {

  /** Returns the agent context carried by this result, or {@code null} if this result has none. */
  @Nullable AgentContext agentContext();

  /** Agent initialization provisioned the agent and resolved its tools. Proceed to converse. */
  record ReadyToConverse(AgentContext agentContext, List<ToolCallResult> toolCallResults)
      implements AgentInitializationResult {}

  /** Gateway tools require discovery. Dispatch these tool calls, then await their results. */
  record DiscoverTools(AgentContext agentContext, List<ToolCall> toolDiscoveryToolCalls)
      implements AgentInitializationResult {}

  /** Tool discovery started, but not all results have arrived. Skip processing this turn. */
  record DeferConversation() implements AgentInitializationResult {
    @Override
    public @Nullable AgentContext agentContext() {
      return null;
    }
  }
}
