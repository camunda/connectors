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
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;

/**
 * A specific gateway tool handler, implementing all the logic necessary to handle gateway tools for
 * a specific type.
 *
 * <p>A single handler is expected to handle gateway tools identified by a specific {@link
 * io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver}.
 */
public interface GatewayToolHandler extends GatewayToolCallTransformer {
  String type();

  GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions);

  /** Defines whether this specific handler is able to handle the given tool call result. */
  boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult);

  /** Handles tool discovery results matching the handlesToolDiscoveryResult() predicate. */
  List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);
}
