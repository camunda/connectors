/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.discovery;

import static io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxToolCallIdentifier.LOCAL_TOOLBOX_NAMESPACE_SEPARATOR;
import static io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxToolCallIdentifier.LOCAL_TOOLBOX_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDefinitionUpdates;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.common.util.CollectionUtils;
import io.camunda.connector.agenticai.common.util.ObjectMapperConstants;
import io.camunda.connector.agenticai.localtoolbox.LocalToolboxErrorCodes;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxOperationDefinitions;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxCallToolResult;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxListToolsResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway tool handler for the local toolbox pattern: a gateway element referencing another
 * deployed process whose ad-hoc sub-process tools are discovered via {@code
 * LocalToolboxClientFunction} (process-definition introspection, not a live tool call to an
 * external server) and executed by creating an instance of the referenced process, driven by {@code
 * LocalToolboxRouterFunction}. Mirrors {@code McpClientGatewayToolHandler}.
 */
public class LocalToolboxGatewayToolHandler implements GatewayToolHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LocalToolboxGatewayToolHandler.class);

  public static final String GATEWAY_TYPE = "localToolbox";
  public static final String PROPERTY_LOCAL_TOOLBOX_CLIENTS = "localToolboxClients";
  public static final String LOCAL_TOOLBOX_TOOLS_DISCOVERY_PREFIX =
      LOCAL_TOOLBOX_PREFIX + "toolsList_";

  private final ObjectMapper objectMapper;

  public LocalToolboxGatewayToolHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String type() {
    return GATEWAY_TYPE;
  }

  @Override
  public boolean isGatewayManaged(String toolName) {
    return LocalToolboxToolCallIdentifier.isLocalToolboxToolCallIdentifier(toolName);
  }

  @Override
  public String resolveElementId(String toolName) {
    return LocalToolboxToolCallIdentifier.fromToolCallName(toolName).elementId();
  }

  @Override
  public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var localToolboxGatewayToolDefinitions =
        extractGatewayToolDefinitions(gatewayToolDefinitions);

    if (localToolboxGatewayToolDefinitions.isEmpty()) {
      return new GatewayToolDiscoveryInitiationResult(agentContext, List.of());
    }

    validateToolDefinitions(localToolboxGatewayToolDefinitions);

    final var updatedAgentContext =
        agentContext.withProperty(
            PROPERTY_LOCAL_TOOLBOX_CLIENTS,
            localToolboxGatewayToolDefinitions.stream().map(GatewayToolDefinition::name).toList());

    final var listToolsOperation = operationAsMap(LocalToolboxOperationDefinitions.listTools());
    final List<ToolCall> discoveryToolCalls =
        localToolboxGatewayToolDefinitions.stream()
            .map(
                gatewayToolDefinition ->
                    new ToolCall(
                        LOCAL_TOOLBOX_TOOLS_DISCOVERY_PREFIX + gatewayToolDefinition.name(),
                        gatewayToolDefinition.name(),
                        listToolsOperation))
            .toList();

    return new GatewayToolDiscoveryInitiationResult(updatedAgentContext, discoveryToolCalls);
  }

  private void validateToolDefinitions(List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var invalidElementIds =
        gatewayToolDefinitions.stream()
            .map(GatewayToolDefinition::name)
            .filter(name -> name.contains(LOCAL_TOOLBOX_NAMESPACE_SEPARATOR))
            .toList();

    if (!invalidElementIds.isEmpty()) {
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_TOOL_DEFINITIONS,
          "Invalid local toolbox activity ID(s) detected: [%s]. Activity IDs must not contain the reserved separator '%s'. Please rename the affected activities in the BPMN model."
              .formatted(
                  invalidElementIds.stream()
                      .map("'%s'"::formatted)
                      .collect(Collectors.joining(", ")),
                  LOCAL_TOOLBOX_NAMESPACE_SEPARATOR));
    }
  }

  @Override
  public GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var localToolboxClientIds = getLocalToolboxClientIds(agentContext);
    final var localToolboxGatewayToolDefinitionIds =
        extractGatewayToolDefinitions(gatewayToolDefinitions).stream()
            .map(GatewayToolDefinition::name)
            .toList();

    return CollectionUtils.computeListItemChanges(
        localToolboxClientIds,
        localToolboxGatewayToolDefinitionIds,
        GatewayToolDefinitionUpdates::new);
  }

  @Override
  public boolean allToolDiscoveryResultsPresent(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var localToolboxClientIds = getLocalToolboxClientIds(agentContext);
    if (localToolboxClientIds.isEmpty()) {
      return true;
    }

    final var toolCallResultIds =
        toolCallResults.stream().map(ToolCallResult::id).collect(Collectors.toSet());
    final var missingToolDiscoveryResults =
        localToolboxClientIds.stream()
            .filter(
                clientId ->
                    !toolCallResultIds.contains(LOCAL_TOOLBOX_TOOLS_DISCOVERY_PREFIX + clientId))
            .toList();

    if (!missingToolDiscoveryResults.isEmpty()) {
      LOGGER.debug(
          "Missing local toolbox tool discovery results for clients: {}",
          missingToolDiscoveryResults);
      return false;
    }

    return true;
  }

  @Override
  public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
    if (StringUtils.isBlank(toolCallResult.id())) {
      return false;
    }

    return toolCallResult.id().startsWith(LOCAL_TOOLBOX_TOOLS_DISCOVERY_PREFIX);
  }

  @Override
  public List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolDiscoveryResults) {
    return toolDiscoveryResults.stream()
        .map(this::toolDefinitionsFromDiscoveryResult)
        .flatMap(List::stream)
        .toList();
  }

  private List<ToolDefinition> toolDefinitionsFromDiscoveryResult(ToolCallResult toolCallResult) {
    final var name = toolCallResult.name();
    if (name == null) {
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_TOOL_DEFINITIONS,
          "Tool call result is missing name");
    }

    final var listToolsResult =
        objectMapper.convertValue(toolCallResult.content(), LocalToolboxListToolsResult.class);
    return listToolsResult.toolDefinitions().stream()
        .map(
            toolDefinition ->
                toolDefinition.withName(
                    new LocalToolboxToolCallIdentifier(name, toolDefinition.name())
                        .fullyQualifiedName()))
        .toList();
  }

  @Override
  public List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls) {
    return toolCalls.stream()
        .map(
            toolCall -> {
              String toolCallName = toolCall.name();
              if (isGatewayManaged(toolCallName)) {
                final var identifier =
                    LocalToolboxToolCallIdentifier.fromToolCallName(toolCallName);
                return new ToolCall(
                    toolCall.id(),
                    identifier.elementId(),
                    operationAsMap(
                        LocalToolboxOperationDefinitions.callTool(
                            identifier.toolName(), toolCall.arguments())));
              }

              return toolCall;
            })
        .toList();
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var localToolboxClientIds = getLocalToolboxClientIds(agentContext);
    return toolCallResults.stream()
        .map(
            toolCallResult -> {
              if (!localToolboxClientIds.contains(toolCallResult.name())) {
                return toolCallResult;
              }
              return toolCallResultFromLocalToolboxCall(toolCallResult);
            })
        .toList();
  }

  private ToolCallResult toolCallResultFromLocalToolboxCall(ToolCallResult toolCallResult) {
    final var name = toolCallResult.name();
    if (name == null) {
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_TOOL_DEFINITIONS,
          "Tool call result is missing name");
    }

    final var callToolResult =
        objectMapper.convertValue(toolCallResult.content(), LocalToolboxCallToolResult.class);
    final var identifier = new LocalToolboxToolCallIdentifier(name, callToolResult.name());

    return ToolCallResult.builder()
        .id(toolCallResult.id())
        .name(identifier.fullyQualifiedName())
        .content(callToolResult.content())
        // completedAt has no fallback resolution on the gateway envelope unwrap - copy explicitly
        .completedAt(toolCallResult.completedAt())
        .build();
  }

  private List<GatewayToolDefinition> extractGatewayToolDefinitions(
      List<GatewayToolDefinition> gatewayToolDefinitions) {
    return gatewayToolDefinitions.stream()
        .filter(gatewayToolDefinition -> GATEWAY_TYPE.equals(gatewayToolDefinition.type()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<String> getLocalToolboxClientIds(AgentContext agentContext) {
    return (List<String>)
        agentContext.properties().getOrDefault(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of());
  }

  private Map<String, Object> operationAsMap(Object operation) {
    return objectMapper.convertValue(
        operation, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }
}
