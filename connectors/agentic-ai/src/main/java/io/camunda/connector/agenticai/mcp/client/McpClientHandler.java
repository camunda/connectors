/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientHandler.class);

  public static final String METHOD_TOOLS_LIST = "tools/list";
  public static final String METHOD_TOOLS_CALL = "tools/call";

  private final McpClientRegistry clientRegistry;
  private final ObjectMapper objectMapper;

  public McpClientHandler(McpClientRegistry clientRegistry, ObjectMapper objectMapper) {
    this.clientRegistry = clientRegistry;
    this.objectMapper = objectMapper;
  }

  public Object handle(McpClientRequest request) {
    final var clientId = request.data().client().clientId();
    final var method = request.data().operation().method();
    final var parameters = request.data().operation().parameters();

    LOGGER.debug("Handling request for method '{}' with on MCP client '{}'", method, clientId);
    final var client = clientRegistry.getClient(clientId);

    return switch (method) {
      case METHOD_TOOLS_LIST:
        // TODO properly serialize tool specifications
        yield client.listTools();
      case METHOD_TOOLS_CALL:
        final var toolExecutionRequest = createToolExecutionRequest(parameters);
        yield client.executeTool(toolExecutionRequest);
      default:
        throw new IllegalArgumentException("Unsupported method: " + method);
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
