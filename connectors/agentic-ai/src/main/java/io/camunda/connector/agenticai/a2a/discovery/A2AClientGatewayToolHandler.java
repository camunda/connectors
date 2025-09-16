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
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class A2AClientGatewayToolHandler implements GatewayToolHandler {
  public static final String GATEWAY_TYPE = "a2aClient";

  public static final String PROPERTY_A2A_CLIENTS = "a2aClients";
  public static final String A2A_PREFIX = "A2A_";
  private static final String A2A_TOOLS_DISCOVERY_PREFIX = A2A_PREFIX + "fetchAgentCard_";
  private static final String DEFAULT_INPUT_SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "message": {
            "type": "string",
            "description": "The instruction or the follow-up message to send to the agent."
          }
        },
        "required": ["message"]
      }
      """;

  private final ObjectMapper objectMapper;
  private final Map<String, Object> defaultInputSchema;

  public A2AClientGatewayToolHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    try {
      this.defaultInputSchema =
          objectMapper.readValue(DEFAULT_INPUT_SCHEMA, STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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

    return ToolDefinition.builder()
        .name(new A2AToolCallIdentifier(toolCallResult.name()).fullyQualifiedName())
        .description(toolCallResult.content().toString())
        .inputSchema(defaultInputSchema)
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
              if (A2AToolCallIdentifier.isA2AToolCallIdentifier(toolCallName)) {
                final var toolCallIdentifier = A2AToolCallIdentifier.fromToolCallName(toolCallName);
                return new ToolCall(
                    toolCall.id(),
                    toolCallIdentifier.elementName(),
                    Map.of(
                        "operation",
                        A2AClientOperationConfiguration.SendMessageOperationConfiguration
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

              return toolCallResultFromA2ASendMessage(toolCallResult);
            })
        .toList();
  }

  private ToolCallResult toolCallResultFromA2ASendMessage(ToolCallResult toolCallResult) {
    final var callToolResult =
        objectMapper.convertValue(toolCallResult.content(), A2AClientSendMessageResult.class);
    final var identifier = new A2AToolCallIdentifier(toolCallResult.name());

    final var toolCallResultBuilder =
        ToolCallResult.builder().id(toolCallResult.id()).name(identifier.fullyQualifiedName());

    // directly use the string content if the returned content is a single text content
    if (callToolResult.contents().size() == 1
        && callToolResult.contents().getFirst() instanceof TextContent(String text)) {
      toolCallResultBuilder.content(text);
    } else {
      toolCallResultBuilder.content(callToolResult.contents());
    }

    return toolCallResultBuilder.build();
  }
}
