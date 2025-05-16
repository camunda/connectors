/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools.protocol;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse.ToolCall;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallResult;
import java.util.List;

public interface GatewayToolHandler {

  String type();

  ToolDiscoveryContext initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions);

  boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult);

  List<AdHocToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);

  List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls);

  List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);

  record ToolDiscoveryContext(AgentContext agentContext, List<ToolCall> toolDiscoveryToolCalls) {}
}
