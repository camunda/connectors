<<<<<<<< HEAD:connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/mcp/client/framework/langchain4j/rpc/ToolExecutionRequest.java
========
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ToolCallRequest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallRequest.class);
  private final ObjectMapper objectMapper;

  ToolCallRequest(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public McpClientCallToolResult execute(
      McpClient client,
      AllowDenyList toolNameFilter,
      McpClientOperation.McpClientCallToolOperation.McpClientCallToolOperationParams params) {

    final var toolExecutionRequest = createToolExecutionRequest(params);
    if (!toolNameFilter.isPassing(toolExecutionRequest.name())) {
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
          Optional.ofNullable(result)
              .map(ToolExecutionResult::resultText)
              .filter(StringUtils::isNotBlank)
              .orElse(ToolCallResult.CONTENT_NO_RESULT);

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
      McpClientOperation.McpClientCallToolOperation.McpClientCallToolOperationParams params) {
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
>>>>>>>> a6fb183ae (chore: Cleanup code):connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/mcp/client/framework/langchain4j/rpc/ToolCallRequest.java
