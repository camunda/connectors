/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.mcp.discovery.McpToolCallIdentifier.MCP_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDefinitionUpdates;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperationDefinitions;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.util.CollectionUtils;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientGatewayToolHandler implements GatewayToolHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientGatewayToolHandler.class);

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
  public boolean isGatewayManaged(String toolName) {
    return McpToolCallIdentifier.isMcpToolCallIdentifier(toolName);
  }

  @Override
  public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var mcpGatewayToolDefinitions = extractMcpGatewayToolDefinitions(gatewayToolDefinitions);

    // nothing to discover
    if (mcpGatewayToolDefinitions.isEmpty()) {
      return new GatewayToolDiscoveryInitiationResult(agentContext, List.of());
    }

    final var updatedAgentContext =
        agentContext.withProperty(
            PROPERTY_MCP_CLIENTS,
            mcpGatewayToolDefinitions.stream().map(GatewayToolDefinition::name).toList());

    final var listToolsOperation =
        mcpClientOperationAsMap(McpClientOperationDefinitions.listTools());
    List<ToolCall> discoveryToolCalls =
        mcpGatewayToolDefinitions.stream()
            .map(
                gatewayToolDefinition ->
                    new ToolCall(
                        MCP_TOOLS_DISCOVERY_PREFIX + gatewayToolDefinition.name(),
                        gatewayToolDefinition.name(),
                        listToolsOperation))
            .toList();

    return new GatewayToolDiscoveryInitiationResult(updatedAgentContext, discoveryToolCalls);
  }

  @Override
  public GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var mcpClientIds = getMcpClientIds(agentContext);
    final var mcpGatewayToolDefinitionIds =
        extractMcpGatewayToolDefinitions(gatewayToolDefinitions).stream()
            .map(GatewayToolDefinition::name)
            .toList();

    return CollectionUtils.computeListItemChanges(
        mcpClientIds, mcpGatewayToolDefinitionIds, GatewayToolDefinitionUpdates::new);
  }

  @Override
  public boolean allToolDiscoveryResultsPresent(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var mcpClientIds = getMcpClientIds(agentContext);
    if (mcpClientIds.isEmpty()) {
      return true;
    }

    final var toolCallResultIds =
        toolCallResults.stream().map(ToolCallResult::id).collect(Collectors.toSet());
    final var missingToolDiscoveryResults =
        mcpClientIds.stream()
            .filter(clientId -> !toolCallResultIds.contains(MCP_TOOLS_DISCOVERY_PREFIX + clientId))
            .toList();

    if (!missingToolDiscoveryResults.isEmpty()) {
      LOGGER.debug(
          "Missing MCP client tool discovery results for clients: {}", missingToolDiscoveryResults);
      return false;
    }

    return true;
  }

  @Override
  public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
    if (StringUtils.isBlank(toolCallResult.id())) {
      return false;
    }

    return toolCallResult.id().startsWith(MCP_TOOLS_DISCOVERY_PREFIX);
  }

  @Override
  public List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolDiscoveryResults) {

    return toolDiscoveryResults.stream()
        .map(this::toolDefinitionsFromDiscoveryResult)
        .flatMap(List::stream)
        .toList();
  }

  /**
   * Resolves an MCP discovery result to a list of tool definitions mapping to the same MCP client
   * element activity. Each tool is prefixed with a MCP prefix and the activity name in order to
   * uniquely identify it.
   */
  private List<ToolDefinition> toolDefinitionsFromDiscoveryResult(ToolCallResult toolCallResult) {
    final var listToolsResult =
        objectMapper.convertValue(toolCallResult.content(), McpClientListToolsResult.class);
    return listToolsResult.toolDefinitions().stream()
        .map(
            toolDefinition ->
                ToolDefinition.builder()
                    .name(fullyQualifiedToolName(toolCallResult, toolDefinition))
                    .description(toolDefinition.description())
                    .inputSchema(toolDefinition.inputSchema())
                    .build())
        .toList();
  }

  private String fullyQualifiedToolName(
      ToolCallResult toolCallResult, McpToolDefinition toolDefinition) {
    final var identifier = new McpToolCallIdentifier(toolCallResult.name(), toolDefinition.name());
    return identifier.fullyQualifiedName();
  }

  /**
   * Transforms tool calls with the fully qualified MCP tool call identifier to route to the MCP
   * client activity without the prefix. The actual tool name within the MCP client is extracted
   * from the tool call name.
   */
  @Override
  public List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls) {
    return toolCalls.stream()
        .map(
            toolCall -> {
              String toolCallName = toolCall.name();
              if (isGatewayManaged(toolCallName)) {
                final var toolCallIdentifier = McpToolCallIdentifier.fromToolCallName(toolCallName);
                return new ToolCall(
                    toolCall.id(),
                    toolCallIdentifier.elementName(),
                    mcpClientOperationAsMap(
                        McpClientOperationDefinitions.callTool(
                            toolCallIdentifier.mcpToolName(), toolCall.arguments())));
              }

              return toolCall;
            })
        .toList();
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {

    final var mcpClientIds = getMcpClientIds(agentContext);
    return toolCallResults.stream()
        .map(
            toolCallResult -> {
              if (!mcpClientIds.contains(toolCallResult.name())) {
                return toolCallResult;
              }

              return toolCallResultFromMcpToolCall(toolCallResult);
            })
        .toList();
  }

  private List<GatewayToolDefinition> extractMcpGatewayToolDefinitions(
      List<GatewayToolDefinition> gatewayToolDefinitions) {
    return gatewayToolDefinitions.stream()
        .filter(gatewayToolDefinition -> GATEWAY_TYPE.equals(gatewayToolDefinition.type()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<String> getMcpClientIds(AgentContext agentContext) {
    return (List<String>) agentContext.properties().getOrDefault(PROPERTY_MCP_CLIENTS, List.of());
  }

  private ToolCallResult toolCallResultFromMcpToolCall(ToolCallResult toolCallResult) {
    final var callToolResult =
        objectMapper.convertValue(toolCallResult.content(), McpClientCallToolResult.class);
    final var identifier = new McpToolCallIdentifier(toolCallResult.name(), callToolResult.name());

    final var toolCallResultBuilder =
        ToolCallResult.builder().id(toolCallResult.id()).name(identifier.fullyQualifiedName());

    // directly use the string content if the returned content is a single text content
    if (callToolResult.content().size() == 1
        && callToolResult.content().getFirst() instanceof McpTextContent textContent) {
      toolCallResultBuilder.content(textContent.text());
    } else {
      toolCallResultBuilder.content(callToolResult.content());
    }

    return toolCallResultBuilder.build();
  }

  private Map<String, Object> mcpClientOperationAsMap(McpClientOperation mcpClientOperation) {
    return objectMapper.convertValue(
        mcpClientOperation, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }
}
