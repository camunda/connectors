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
    return switch (operation.method()) {
      case LIST_TOOLS ->
          new ListToolsRequest(toolSpecificationConverter)
              .execute(client, filterOptions.toolFilters());
      case CALL_TOOL ->
          new ToolCallRequest(objectMapper)
              .execute(client, filterOptions.toolFilters(), operation.parameters());
      case LIST_RESOURCES -> new ListResourcesRequest().execute(client);
      case LIST_RESOURCE_TEMPLATES -> new ListResourceTemplatesRequest().execute(client);
      case READ_RESOURCE, LIST_PROMPTS, GET_PROMPT ->
          throw new UnsupportedOperationException(
              "This operation is not supported yet: " + operation.method().methodName());
    };
  }
}
