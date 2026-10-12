/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.AgentTool;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistry;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Maps agentic-ai {@link ToolDefinition} instances into Camunda client {@link AgentTool} objects.
 * Resolves BPMN element IDs for gateway-backed tools via {@link GatewayToolHandlerRegistry} and
 * falls back to the tool name for ad-hoc tools. A local tool has no BPMN element: its element ID is
 * left empty.
 */
public class AgentInstanceToolMapper {

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final LocalToolRegistry localToolRegistry;

  public AgentInstanceToolMapper(
      GatewayToolHandlerRegistry gatewayToolHandlers, LocalToolRegistry localToolRegistry) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.localToolRegistry = localToolRegistry;
  }

  /**
   * Maps the tools of the agent. A local tool has no BPMN element; {@code localTools} tells whether
   * the agent instance has the local tools feature, without which no tool is a local tool.
   */
  public List<AgentTool> mapTools(List<ToolDefinition> toolDefinitions, boolean localTools) {
    return toolDefinitions.stream().map(tool -> mapTool(tool, localTools)).toList();
  }

  private AgentTool mapTool(ToolDefinition toolDefinition, boolean localTools) {
    final @Nullable String elementId =
        localTools && localToolRegistry.toolNames().contains(toolDefinition.name())
            ? null
            : gatewayToolHandlers
                .resolveElementId(toolDefinition.name())
                .orElse(toolDefinition.name());
    return AgentTool.of(toolDefinition.name(), toolDefinition.description(), elementId);
  }
}
