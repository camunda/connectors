/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinitionBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListToolsRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListToolsRequest.class);

  private final String clientId;

  ListToolsRequest(String clientId) {
    this.clientId = clientId;
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
                        .title(tool.title())
                        .description(tool.description())
                        .inputSchema(tool.inputSchema())
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
}
