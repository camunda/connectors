/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientCallToolOperation;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation.McpClientListToolsOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;

public class Langchain4JMcpClientExecutor {
  private final ObjectMapper objectMapper;
  private final ToolSpecificationConverter toolSpecificationConverter;

  public Langchain4JMcpClientExecutor(
      ObjectMapper objectMapper, ToolSpecificationConverter toolSpecificationConverter) {
    this.objectMapper = objectMapper;
    this.toolSpecificationConverter = toolSpecificationConverter;
  }

  public McpClientResult execute(
      McpClient client, McpClientOperation operation, FilterOptions filterOptions) {
    return switch (operation) {
      case McpClientListToolsOperation ignored ->
          new ListToolsRequest(toolSpecificationConverter)
              .execute(client, filterOptions.toolFilters());
      case McpClientCallToolOperation callTool ->
          new ToolExecutionRequest(objectMapper)
              .execute(client, filterOptions.toolFilters(), callTool.params());
      case McpClientOperation.McpClientListResourcesOperation ignored ->
          new ListResourcesRequest().execute(client);
    };
  }
}
