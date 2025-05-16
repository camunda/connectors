/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static io.camunda.connector.agenticai.mcp.client.model.McpClientMessage.METHOD_TOOLS_CALL;
import static io.camunda.connector.agenticai.mcp.client.model.McpClientMessage.METHOD_TOOLS_LIST;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.aiagent.tools.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.McpClientMessage;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpClientToolCallResult;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientHandler.class);

  private final McpClientRegistry clientRegistry;
  private final ObjectMapper objectMapper;
  private final ToolSpecificationConverter toolSpecificationConverter;

  public McpClientHandler(
      McpClientRegistry clientRegistry,
      ObjectMapper objectMapper,
      ToolSpecificationConverter toolSpecificationConverter) {
    this.clientRegistry = clientRegistry;
    this.objectMapper = objectMapper;
    this.toolSpecificationConverter = toolSpecificationConverter;
  }

  public Object handle(McpClientRequest request) {
    final var clientId = request.data().client().clientId();
    final var message =
        objectMapper.convertValue(request.data().operation(), McpClientMessage.class);

    LOGGER.debug(
        "Handling request for method '{}' with on MCP client '{}'", message.method(), clientId);
    final var client = clientRegistry.getClient(clientId);

    return switch (message.method()) {
      case METHOD_TOOLS_LIST:
        final var toolDefinitions =
            client.listTools().stream()
                .map(
                    toolSpecification ->
                        new AdHocToolDefinition(
                            toolSpecification.name(),
                            toolSpecification.description(),
                            toolSpecificationConverter.schemaAsMap(toolSpecification.parameters())))
                .toList();
        yield new McpClientListToolsResult(toolDefinitions);
      case METHOD_TOOLS_CALL:
        final var toolExecutionRequest = createToolExecutionRequest(message.params());
        yield new McpClientToolCallResult(
            toolExecutionRequest.name(), client.executeTool(toolExecutionRequest));
      default:
        throw new IllegalArgumentException("Unsupported method: " + message.method());
    };
  }

  private ToolExecutionRequest createToolExecutionRequest(Map<String, Object> parameters) {
    final var toolName = parameters.get("name");
    if (!(toolName instanceof String toolNameString)) {
      throw new IllegalArgumentException("Tool name must be a string");
    }

    final var arguments = parameters.get("arguments");
    if (!(arguments instanceof Map<?, ?> argumentsMap)) {
      throw new IllegalArgumentException("Tool arguments must be a map");
    }

    try {
      return ToolExecutionRequest.builder()
          .name(toolNameString)
          .arguments(objectMapper.writeValueAsString(argumentsMap))
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to create tool execution request for tool '%s'".formatted(toolNameString), e);
    }
  }
}
