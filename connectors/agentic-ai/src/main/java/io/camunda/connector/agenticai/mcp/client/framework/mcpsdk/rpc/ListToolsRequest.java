/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinitionBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListToolsRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListToolsRequest.class);

  private final String clientId;
  private final ObjectMapper objectMapper;

  ListToolsRequest(String clientId, ObjectMapper objectMapper) {
    this.clientId = clientId;
    this.objectMapper = objectMapper;
  }

  public McpClientListToolsResult execute(McpSyncClient client, AllowDenyList toolNameFilter) {
    LOGGER.debug("MCP({}): Executing list tools", clientId);

    final var toolSpecifications = client.listTools().tools();
    if (toolSpecifications.isEmpty()) {
      LOGGER.debug("MCP({}): No tools found", clientId);
      return new McpClientListToolsResult(Collections.emptyList());
    }

    final var filteredToolSpecifications =
        toolSpecifications.stream()
            .filter(toolSpecification -> toolNameFilter.isPassing(toolSpecification.name()))
            .toList();
    final var filteredToolDefinitions =
        filteredToolSpecifications.stream()
            .map(
                tool ->
                    McpToolDefinitionBuilder.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(parseToolParameters(tool.inputSchema()))
                        .build())
            .toList();

    if (filteredToolDefinitions.isEmpty()) {
      LOGGER.warn(
          "MCP({}): No tools left after filtering tools. Filter: {}", clientId, toolNameFilter);
      return new McpClientListToolsResult(Collections.emptyList());
    }

    LOGGER.debug(
        "MCP({}): Resolved list of tools: {}",
        clientId,
        filteredToolDefinitions.stream().map(McpToolDefinition::name).toList());

    return new McpClientListToolsResult(filteredToolDefinitions);
  }

  private Map<String, Object> parseToolParameters(McpSchema.JsonSchema inputSchema) {
    return objectMapper.convertValue(
        inputSchema, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }
}
