/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.execution;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import java.util.Map;

public interface McpClientDelegate extends AutoCloseable {

  String clientId();

  McpClientListToolsResult listTools(AllowDenyList filter);

  McpClientCallToolResult callTool(Map<String, Object> params, AllowDenyList filter);

  McpClientListResourcesResult listResources(AllowDenyList filter);

  McpClientListResourceTemplatesResult listResourceTemplates(AllowDenyList filter);

  McpClientReadResourceResult readResource(Map<String, Object> params, AllowDenyList filter);

  McpClientListPromptsResult listPrompts(AllowDenyList filter);

  McpClientGetPromptResult getPrompt(Map<String, Object> params, AllowDenyList filter);
}
