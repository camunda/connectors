/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery;

import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class A2aGatewayToolHandler implements GatewayToolHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aGatewayToolHandler.class);

  public static final String GATEWAY_TYPE = "a2aClient";
  public static final String PROPERTY_A2A_CLIENTS = "a2aClients";
  public static final String A2A_PREFIX = "A2A_";
  private static final String A2A_TOOLS_DISCOVERY_PREFIX = A2A_PREFIX + "fetchAgentCard_";
  private static final String TOOL_INPUT_JSON_SCHEMA_RESOURCE = "a2a/tool-input-schema.json";

  private final ObjectMapper objectMapper;
  private final Map<String, Object> toolInputSchema;

  public A2aGatewayToolHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.toolInputSchema = loadToolInputSchema();
  }

  @Override
  public String type() {
    return GATEWAY_TYPE;
  }

  @Override
  public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var a2aGatewayToolDefinitions =
        gatewayToolDefinitions.stream()
            .filter(gatewayToolDefinition -> GATEWAY_TYPE.equals(gatewayToolDefinition.type()))
            .toList();

    // nothing to discover
    if (a2aGatewayToolDefinitions.isEmpty()) {
      return new GatewayToolDiscoveryInitiationResult(agentContext, List.of());
    }

    final var updatedAgentContext =
        agentContext.withProperty(
            PROPERTY_A2A_CLIENTS,
            a2aGatewayToolDefinitions.stream().map(GatewayToolDefinition::name).toList());

    Map<String, Object> fetchAgentCardOperation =
        Map.of("operation", FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID);
    List<ToolCall> discoveryToolCalls =
        a2aGatewayToolDefinitions.stream()
            .map(
                gatewayToolDefinition ->
                    new ToolCall(
                        A2A_TOOLS_DISCOVERY_PREFIX + gatewayToolDefinition.name(),
                        gatewayToolDefinition.name(),
                        fetchAgentCardOperation))
            .toList();

    return new GatewayToolDiscoveryInitiationResult(updatedAgentContext, discoveryToolCalls);
  }

  @Override
  public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
    if (StringUtils.isBlank(toolCallResult.id())) {
      return false;
    }

    return toolCallResult.id().startsWith(A2A_TOOLS_DISCOVERY_PREFIX);
  }

  @Override
  public List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolDiscoveryResults) {

    return toolDiscoveryResults.stream().map(this::toolDefinitionsFromDiscoveryResult).toList();
  }

  private ToolDefinition toolDefinitionsFromDiscoveryResult(ToolCallResult toolCallResult) {
    if (toolCallResult.content() == null) {
      throw new IllegalArgumentException(
          "Tool call result content for A2A client tool discovery is null.");
    }

    if (!(toolCallResult.content() instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(
          "Tool call result content for A2A client tool discovery is not a map.");
    }

    String toolName = new A2aToolCallIdentifier(toolCallResult.name()).fullyQualifiedName();
    return ToolDefinition.builder()
        .name(toolName)
        .description(createToolDefinitionDescription(toolCallResult))
        .inputSchema(toolInputSchema)
        .build();
  }

  /**
   * Transforms tool calls with the fully qualified tool call identifier to route to the A2A client
   * activity without the prefix.
   */
  @Override
  public List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls) {
    return toolCalls.stream()
        .map(
            toolCall -> {
              String toolCallName = toolCall.name();
              if (A2aToolCallIdentifier.isA2aToolCallIdentifier(toolCallName)) {
                final var toolCallIdentifier = A2aToolCallIdentifier.fromToolCallName(toolCallName);
                return new ToolCall(
                    toolCall.id(),
                    toolCallIdentifier.elementName(),
                    Map.of(
                        "operation",
                        A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration
                            .SEND_MESSAGE_ID,
                        "params",
                        toolCall.arguments()));
              }

              return toolCall;
            })
        .toList();
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {

    // noinspection unchecked
    final var a2aClients =
        (List<String>) agentContext.properties().getOrDefault(PROPERTY_A2A_CLIENTS, List.of());

    return toolCallResults.stream()
        .map(
            toolCallResult -> {
              if (!a2aClients.contains(toolCallResult.name())) {
                return toolCallResult;
              }

              return toolCallResultFromA2aSendMessage(toolCallResult);
            })
        .toList();
  }

  private ToolCallResult toolCallResultFromA2aSendMessage(ToolCallResult toolCallResult) {
    final var sendMessageResult =
        objectMapper.convertValue(toolCallResult.content(), A2aSendMessageResult.class);
    final var identifier = new A2aToolCallIdentifier(toolCallResult.name());

    final var toolCallResultBuilder =
        ToolCallResult.builder().id(toolCallResult.id()).name(identifier.fullyQualifiedName());

    toolCallResultBuilder.content(sendMessageResult);

    return toolCallResultBuilder.build();
  }

  /**
   * Creates a description for the tool definition by serializing the agent card and appending a
   * fixed explanation of the tool call result structure.
   */
  private String createToolDefinitionDescription(ToolCallResult toolCallResult) {
    try {
      String agentCard = objectMapper.writeValueAsString(toolCallResult.content());
      return "This tool allows interaction with the remote agent represented by the following agent card:\n%s"
          .formatted(agentCard);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to serialize A2A client tool description for tool %s: %s"
              .formatted(toolCallResult.name(), toolCallResult.content()),
          e);
    }
  }

  private Map<String, Object> loadToolInputSchema() {
    try {
      ClassPathResource resource = new ClassPathResource(TOOL_INPUT_JSON_SCHEMA_RESOURCE);
      return objectMapper.readValue(resource.getInputStream(), STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (IOException e) {
      LOGGER.error(
          "Failed to load A2A tool input schema from {}: {}",
          TOOL_INPUT_JSON_SCHEMA_RESOURCE,
          e.getMessage());
      throw new IllegalStateException(e);
    }
  }
}
