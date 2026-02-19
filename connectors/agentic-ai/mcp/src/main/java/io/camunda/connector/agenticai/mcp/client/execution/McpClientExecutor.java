/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.execution;

import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.filters.FilterOptions;
import io.camunda.connector.agenticai.mcp.client.model.McpClientOperation;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import org.jspecify.annotations.NonNull;

public class McpClientExecutor {
  private final McpClientResultDocumentHandler clientResultDocumentHandler;

  public McpClientExecutor(McpClientResultDocumentHandler clientResultDocumentHandler) {
    this.clientResultDocumentHandler = clientResultDocumentHandler;
  }

  public McpClientResult execute(
      McpClientDelegate clientDelegate, McpClientOperation operation, FilterOptions filterOptions) {
    var result = executeRequest(clientDelegate, operation, filterOptions);

    return clientResultDocumentHandler.convertBinariesToDocumentsIfPresent(result);
  }

  private @NonNull McpClientResult executeRequest(
      McpClientDelegate clientDelegate, McpClientOperation operation, FilterOptions filterOptions) {
    return switch (operation.method()) {
      case LIST_TOOLS -> clientDelegate.listTools(filterOptions.toolFilters());
      case CALL_TOOL -> clientDelegate.callTool(operation.params(), filterOptions.toolFilters());
      case LIST_RESOURCES -> clientDelegate.listResources(filterOptions.resourceFilters());
      case LIST_RESOURCE_TEMPLATES ->
          clientDelegate.listResourceTemplates(filterOptions.resourceFilters());
      case READ_RESOURCE ->
          clientDelegate.readResource(operation.params(), filterOptions.resourceFilters());
      case LIST_PROMPTS -> clientDelegate.listPrompts(filterOptions.promptFilters());
      case GET_PROMPT ->
          clientDelegate.getPrompt(operation.params(), filterOptions.promptFilters());
    };
  }
}
