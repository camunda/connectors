/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.AgentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import java.util.List;

/**
 * Maps agentic-ai {@link ToolDefinition} instances into Camunda client {@link AgentTool} objects.
 * Resolves BPMN element IDs for gateway-backed tools via {@link GatewayToolHandlerRegistry} and
 * falls back to the tool name for ad-hoc tools. A runtime tool has no BPMN element: its element ID
 * is left empty.
 */
public class AgentInstanceToolMapper {

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final RuntimeToolRegistry runtimeToolRegistry;

  public AgentInstanceToolMapper(
      GatewayToolHandlerRegistry gatewayToolHandlers, RuntimeToolRegistry runtimeToolRegistry) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.runtimeToolRegistry = runtimeToolRegistry;
  }

  /**
   * Maps the tools of the agent. A runtime tool has no BPMN element; an agent instance without the
   * runtime tools feature has no runtime tool.
   */
  public List<AgentTool> mapTools(AgentContext agentContext, List<ToolDefinition> toolDefinitions) {
    return toolDefinitions.stream().map(tool -> mapTool(agentContext, tool)).toList();
  }

  private AgentTool mapTool(AgentContext agentContext, ToolDefinition toolDefinition) {
    final var name = toolDefinition.name();
    if (runtimeToolRegistry.kindOf(agentContext, name) == ToolKind.RUNTIME) {
      return AgentTool.of(name, toolDefinition.description(), null);
    }
    final var elementId = gatewayToolHandlers.resolveElementId(name).orElse(name);
    return AgentTool.of(name, toolDefinition.description(), elementId);
  }
}
