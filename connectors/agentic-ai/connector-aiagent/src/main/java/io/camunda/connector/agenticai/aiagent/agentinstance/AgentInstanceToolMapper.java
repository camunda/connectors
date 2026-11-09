/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.AgentTool;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.util.List;

/**
 * Maps agentic-ai {@link ToolDefinition} instances into Camunda client {@link AgentTool} objects.
 * Resolves BPMN element IDs for gateway-backed tools via {@link GatewayToolHandlerRegistry} and
 * falls back to the tool name for ad-hoc tools. A runtime tool has no BPMN element: its element ID
 * is left empty.
 */
public class AgentInstanceToolMapper {

  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public AgentInstanceToolMapper(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  public List<AgentTool> mapTools(List<ToolDefinition> toolDefinitions) {
    return toolDefinitions.stream().map(this::mapTool).toList();
  }

  private AgentTool mapTool(ToolDefinition toolDefinition) {
    final var name = toolDefinition.name();
    if (toolDefinition.kind() == ToolKind.RUNTIME) {
      return AgentTool.of(name, toolDefinition.description(), null);
    }
    final var elementId = gatewayToolHandlers.resolveElementId(name).orElse(name);
    return AgentTool.of(name, toolDefinition.description(), elementId);
  }
}
