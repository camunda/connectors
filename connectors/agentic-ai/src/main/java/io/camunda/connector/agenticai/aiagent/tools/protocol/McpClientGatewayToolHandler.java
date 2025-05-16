/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools.protocol;

import static io.camunda.connector.agenticai.aiagent.tools.protocol.McpClientGatewayToolDefinitionResolver.PROPERTY_GATEWAY_TYPE;
import static io.camunda.connector.agenticai.aiagent.tools.protocol.McpToolCallIdentifier.MCP_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse.ToolCall;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallResult;
import io.camunda.connector.agenticai.mcp.client.model.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.McpClientMessage;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolCallResult;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.List;
import java.util.Map;

public class McpClientGatewayToolHandler implements GatewayToolHandler {

  public static final String GATEWAY_TYPE = "mcpClient";

  public static final String PROPERTY_MCP_CLIENTS = "mcpClients";
  public static final String MCP_TOOLS_DISCOVERY_PREFIX = MCP_PREFIX + "toolsList_";

  private final ObjectMapper objectMapper;

  public McpClientGatewayToolHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String type() {
    return GATEWAY_TYPE;
  }

  @Override
  public ToolDiscoveryContext initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {

    final var mcpGatewayToolDefinitions =
        gatewayToolDefinitions.stream()
            .filter(
                gatewayToolDefinition -> {
                  final var gatewayType =
                      gatewayToolDefinition.properties().get(PROPERTY_GATEWAY_TYPE);
                  return GATEWAY_TYPE.equals(gatewayType);
                })
            .toList();

    // nothing to discover
    if (mcpGatewayToolDefinitions.isEmpty()) {
      return new ToolDiscoveryContext(agentContext, List.of());
    }

    final var updatedAgentContext =
        agentContext.withProperty(
            PROPERTY_MCP_CLIENTS,
            mcpGatewayToolDefinitions.stream().map(GatewayToolDefinition::name).toList());

    final var clientMessage = mcpClientMessageAsMap(McpClientMessage.listTools());
    List<ToolCall> discoveryToolCalls =
        mcpGatewayToolDefinitions.stream()
            .map(
                gatewayToolDefinition ->
                    new ToolCall(
                        MCP_TOOLS_DISCOVERY_PREFIX + gatewayToolDefinition.name(),
                        gatewayToolDefinition.name(),
                        clientMessage))
            .toList();

    return new ToolDiscoveryContext(updatedAgentContext, discoveryToolCalls);
  }

  public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
    return toolCallResult.id().startsWith(MCP_TOOLS_DISCOVERY_PREFIX);
  }

  @Override
  public List<AdHocToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolDiscoveryResults) {

    return toolDiscoveryResults.stream()
        .map(this::toolDefinitionsFromDiscoveryResult)
        .flatMap(List::stream)
        .toList();
  }

  /** Resolves a MCP discovery result to a list of tool definitions mapping to the same activity. */
  private List<AdHocToolDefinition> toolDefinitionsFromDiscoveryResult(
      ToolCallResult toolCallResult) {
    final var listToolsResult =
        objectMapper.convertValue(toolCallResult.content(), McpClientListToolsResult.class);
    return listToolsResult.toolDefinitions().stream()
        .map(
            toolDefinition ->
                new AdHocToolDefinition(
                    new McpToolCallIdentifier(toolCallResult.name(), toolDefinition.name())
                        .fullyQualifiedName(),
                    toolDefinition.description(),
                    toolDefinition.inputSchema()))
        .toList();
  }

  @Override
  public List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls) {
    return toolCalls.stream()
        .map(
            toolCall -> {
              String toolCallName = toolCall.metadata().name();
              if (McpToolCallIdentifier.isMcpToolCallIdentifier(toolCallName)) {
                final var toolCallIdentifier = McpToolCallIdentifier.fromToolCallName(toolCallName);

                return new ToolCall(
                    toolCall.metadata().id(),
                    toolCallIdentifier.elementName(),
                    mcpClientMessageAsMap(
                        McpClientMessage.callTool(
                            toolCallIdentifier.mcpToolName(), toolCall.arguments())));
              }

              return toolCall;
            })
        .toList();
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {

    //noinspection unchecked
    final var mcpClients =
        (List<String>) agentContext.properties().getOrDefault(PROPERTY_MCP_CLIENTS, List.of());

    return toolCallResults.stream()
        .map(
            toolCallResult -> {
              if (!mcpClients.contains(toolCallResult.name())) {
                return toolCallResult;
              }

              return toolCallResultFromMcpToolCall(toolCallResult);
            })
        .toList();
  }

  private ToolCallResult toolCallResultFromMcpToolCall(ToolCallResult toolCallResult) {
    final var mcpClientToolCallResult =
        objectMapper.convertValue(toolCallResult.content(), McpClientToolCallResult.class);
    final var identifier =
        new McpToolCallIdentifier(toolCallResult.name(), mcpClientToolCallResult.toolName());

    return new ToolCallResult(
        toolCallResult.id(), identifier.fullyQualifiedName(), mcpClientToolCallResult.result());
  }

  private Map<String, Object> mcpClientMessageAsMap(McpClientMessage mcpClientMessage) {
    return objectMapper.convertValue(
        mcpClientMessage, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }
}
