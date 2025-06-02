/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation.McpClientCallToolOperationParams;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpClientHandler implements McpClientHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(Langchain4JMcpClientHandler.class);

  private final McpClientRegistry<McpClient> clientRegistry;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final ObjectMapper objectMapper;

  public Langchain4JMcpClientHandler(
      McpClientRegistry<McpClient> clientRegistry,
      ToolSpecificationConverter toolSpecificationConverter,
      ObjectMapper objectMapper) {
    this.clientRegistry = clientRegistry;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public McpClientResult handle(McpClientRequest request) {
    final var clientId = request.data().client().clientId();
    final var message =
        objectMapper.convertValue(request.data().operation(), McpClientOperation.class);

    LOGGER.debug(
        "Handling request for method '{}' with on MCP client '{}'", message.method(), clientId);
    final var client = clientRegistry.getClient(clientId);

    return switch (message) {
      case McpClientOperation.McpClientListToolsOperation ignored -> {
        final var toolDefinitions =
            client.listTools().stream().map(toolSpecificationConverter::asToolDefinition).toList();
        yield new McpClientListToolsResult(toolDefinitions);
      }

      case McpClientCallToolOperation callTool -> executeTool(client, callTool.params());
    };
  }

  private McpClientCallToolResult executeTool(
      McpClient client, McpClientCallToolOperationParams params) {
    final var toolExecutionRequest = createToolExecutionRequest(params);

    try {
      final var result = client.executeTool(toolExecutionRequest);

      LOGGER.debug("Executed tool '{}' with result: {}", toolExecutionRequest.name(), result);

      return new McpClientCallToolResult(
          toolExecutionRequest.name(), List.of(TextContent.textContent(result)), false);
    } catch (Exception e) {
      LOGGER.error("Failed to execute tool '{}'", toolExecutionRequest.name(), e);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Error executing tool '%s': %s"
                      .formatted(toolExecutionRequest.name(), e.getMessage()))),
          true);
    }
  }

  private ToolExecutionRequest createToolExecutionRequest(McpClientCallToolOperationParams params) {
    if (params == null || params.name() == null) {
      throw new IllegalArgumentException("Tool name must not be null");
    }

    final var arguments = Optional.ofNullable(params.arguments()).orElseGet(Collections::emptyMap);

    try {
      return ToolExecutionRequest.builder()
          .name(params.name())
          .arguments(objectMapper.writeValueAsString(arguments))
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to create tool execution request for tool '%s'".formatted(params.name()), e);
    }
  }
}
