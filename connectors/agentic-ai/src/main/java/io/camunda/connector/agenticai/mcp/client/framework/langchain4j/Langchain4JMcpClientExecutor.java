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
import io.camunda.connector.agenticai.mcp.client.McpToolNameFilter;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4JMcpClientExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(Langchain4JMcpClientExecutor.class);
  private static final String NO_RESULT_MESSAGE = "Tool execution returned no result";

  private final ObjectMapper objectMapper;
  private final ToolSpecificationConverter toolSpecificationConverter;

  public Langchain4JMcpClientExecutor(
      ObjectMapper objectMapper, ToolSpecificationConverter toolSpecificationConverter) {
    this.objectMapper = objectMapper;
    this.toolSpecificationConverter = toolSpecificationConverter;
  }

  public McpClientResult execute(
      McpClient client, McpClientOperation operation, McpToolNameFilter toolNameFilter) {
    return switch (operation) {
      case McpClientListToolsOperation ignored -> listTools(client, toolNameFilter);
      case McpClientCallToolOperation callTool ->
          executeTool(client, toolNameFilter, callTool.params());
    };
  }

  private McpClientListToolsResult listTools(McpClient client, McpToolNameFilter toolNameFilter) {
    LOGGER.debug("MCP({}): Executing list tools", client.key());

    final var toolSpecifications = client.listTools();
    if (toolSpecifications.isEmpty()) {
      LOGGER.warn("MCP({}): No tools found", client.key());
      return new McpClientListToolsResult(Collections.emptyList());
    }

    final var filteredToolSpecifications =
        toolSpecifications.stream()
            .filter(toolSpecification -> toolNameFilter.test(toolSpecification.name()))
            .toList();
    final var filteredToolDefinitions =
        filteredToolSpecifications.stream()
            .map(toolSpecificationConverter::asToolDefinition)
            .toList();

    if (filteredToolDefinitions.isEmpty()) {
      LOGGER.warn(
          "MCP({}): No tools left after filtering tools. Filter: {}", client.key(), toolNameFilter);
      return new McpClientListToolsResult(Collections.emptyList());
    }

    LOGGER.debug(
        "MCP({}): Resolved list of tools: {}",
        client.key(),
        filteredToolDefinitions.stream().map(ToolDefinition::name).toList());

    return new McpClientListToolsResult(filteredToolDefinitions);
  }

  private McpClientCallToolResult executeTool(
      McpClient client,
      McpToolNameFilter toolNameFilter,
      McpClientCallToolOperation.McpClientCallToolOperationParams params) {
    final var toolExecutionRequest = createToolExecutionRequest(params);
    if (!toolNameFilter.test(toolExecutionRequest.name())) {
      LOGGER.error(
          "MCP({}): Tool '{}' is not included in the filter {}.",
          client.key(),
          toolExecutionRequest.name(),
          toolNameFilter);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Executing tool '%s' is not allowed by filter configuration: %s"
                      .formatted(toolExecutionRequest.name(), toolNameFilter))),
          true);
    }

    LOGGER.debug(
        "MCP({}): Executing tool '{}' with arguments: {}",
        client.key(),
        params.name(),
        params.arguments());

    try {
      final var result = client.executeTool(toolExecutionRequest);
      LOGGER.debug(
          "MCP({}): Successfully executed tool '{}'", client.key(), toolExecutionRequest.name());

      final var normalizedResult =
          Optional.ofNullable(result).filter(StringUtils::isNotBlank).orElse(NO_RESULT_MESSAGE);

      return new McpClientCallToolResult(
          toolExecutionRequest.name(), List.of(TextContent.textContent(normalizedResult)), false);
    } catch (Exception e) {
      LOGGER.error(
          "MCP({}): Failed to execute tool '{}'", client.key(), toolExecutionRequest.name(), e);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Error executing tool '%s': %s"
                      .formatted(toolExecutionRequest.name(), e.getMessage()))),
          true);
    }
  }

  private ToolExecutionRequest createToolExecutionRequest(
      McpClientCallToolOperation.McpClientCallToolOperationParams params) {
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
