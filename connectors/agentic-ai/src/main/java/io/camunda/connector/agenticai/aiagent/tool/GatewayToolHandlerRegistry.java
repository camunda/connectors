/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

/**
 * Wrapper around multiple tool handlers, implementing distributing and merging gateway operations
 * across multiple handlers.
 */
public interface GatewayToolHandlerRegistry extends GatewayToolCallTransformer {
  GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions);

  boolean allToolDiscoveryResultsPresent(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);

  GatewayToolDiscoveryResult handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);
}
