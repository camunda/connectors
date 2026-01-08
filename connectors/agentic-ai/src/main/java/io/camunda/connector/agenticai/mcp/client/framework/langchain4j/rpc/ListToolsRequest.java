/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListToolsRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListToolsRequest.class);
  private final ToolSpecificationConverter toolSpecificationConverter;

  ListToolsRequest(ToolSpecificationConverter toolSpecificationConverter) {
    this.toolSpecificationConverter = toolSpecificationConverter;
  }

  public McpClientListToolsResult execute(McpClient client, AllowDenyList toolNameFilter) {
    LOGGER.debug("MCP({}): Executing list tools", client.key());

    final var toolSpecifications = client.listTools();
    if (toolSpecifications.isEmpty()) {
      LOGGER.warn("MCP({}): No tools found", client.key());
      return new McpClientListToolsResult(Collections.emptyList());
    }

    final var filteredToolSpecifications =
        toolSpecifications.stream()
            .filter(toolSpecification -> toolNameFilter.isPassing(toolSpecification.name()))
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
}
