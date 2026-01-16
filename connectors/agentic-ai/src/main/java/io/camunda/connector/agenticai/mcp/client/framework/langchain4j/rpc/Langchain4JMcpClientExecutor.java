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
import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import org.jspecify.annotations.NonNull;

public class Langchain4JMcpClientExecutor {
  private final ObjectMapper objectMapper;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final McpClientResultDocumentHandler clientResultDocumentHandler;

  public Langchain4JMcpClientExecutor(
      ObjectMapper objectMapper,
      ToolSpecificationConverter toolSpecificationConverter,
      McpClientResultDocumentHandler clientResultDocumentHandler) {
    this.objectMapper = objectMapper;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.clientResultDocumentHandler = clientResultDocumentHandler;
  }

  public McpClientResult execute(
      McpClient client, McpClientOperation operation, FilterOptions filterOptions) {
    var result = executeRequest(client, operation, filterOptions);

    return clientResultDocumentHandler.convertBinariesToDocumentsIfPresent(result);
  }

  private @NonNull McpClientResult executeRequest(
      McpClient client, McpClientOperation operation, FilterOptions filterOptions) {
    return switch (operation.method()) {
      case LIST_TOOLS ->
          new ListToolsRequest(toolSpecificationConverter)
              .execute(client, filterOptions.toolFilters());
      case CALL_TOOL ->
          new ToolCallRequest(objectMapper)
              .execute(client, filterOptions.toolFilters(), operation.params());
      case LIST_RESOURCES ->
          new ListResourcesRequest().execute(client, filterOptions.resourceFilters());
      case LIST_RESOURCE_TEMPLATES ->
          new ListResourceTemplatesRequest().execute(client, filterOptions.resourceFilters());
      case LIST_PROMPTS -> new ListPromptsRequest().execute(client, filterOptions.promptFilters());
      case GET_PROMPT -> new GetPromptRequest().execute(client, operation.params());
      case READ_RESOURCE -> new ReadResourceRequest().execute(client, operation.params());
    };
  }
}
