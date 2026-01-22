/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Map;

public class McpSdkMcpClientDelegate implements McpClientDelegate {

  private final McpSyncClient delegate;

  private final ObjectMapper objectMapper;

  public McpSdkMcpClientDelegate(McpSyncClient delegate, ObjectMapper objectMapper) {
    this.delegate = delegate;
    this.objectMapper = objectMapper;
  }

  @Override
  public McpClientListToolsResult listTools(AllowDenyList filter) {
    return new ListToolsRequest(objectMapper).execute(delegate, filter);
  }

  @Override
  public McpClientCallToolResult callTool(Map<String, Object> params, AllowDenyList filter) {
    return new ToolCallRequest(objectMapper).execute(delegate, filter, params);
  }

  @Override
  public McpClientListResourcesResult listResources(AllowDenyList filter) {
    return new ListResourcesRequest().execute(delegate, filter);
  }

  @Override
  public McpClientListResourceTemplatesResult listResourceTemplates(AllowDenyList filter) {
    return new ListResourceTemplatesRequest().execute(delegate, filter);
  }

  @Override
  public McpClientReadResourceResult readResource(
      Map<String, Object> params, AllowDenyList filter) {
    return new ReadResourceRequest().execute(delegate, filter, params);
  }

  @Override
  public McpClientListPromptsResult listPrompts(AllowDenyList filter) {
    return new ListPromptsRequest().execute(delegate, filter);
  }

  @Override
  public McpClientGetPromptResult getPrompt(Map<String, Object> params, AllowDenyList filter) {
    return new GetPromptRequest().execute(delegate, filter, params);
  }

  @Override
  public void close() throws Exception {
    this.delegate.close();
  }
}
