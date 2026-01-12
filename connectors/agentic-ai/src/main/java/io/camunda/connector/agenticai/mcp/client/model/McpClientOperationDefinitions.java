/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import java.util.Map;

/**
 * This is a class holder that provides convenience methods to create McpClientOperation instances
 * for specific operations that are currently required by the application code.
 */
public class McpClientOperationDefinitions {

  /**
   * Creates an instance of McpClientOperation to list tools
   *
   * @return McpClientOperation instance for listing tools
   */
  public static McpClientOperation listTools() {
    return McpClientOperation.of(McpClientOperation.McpMethod.LIST_TOOLS.methodName());
  }

  /**
   * Creates an instance of McpClientOperation to call a specific tool
   *
   * @param toolName the tool to be called
   * @param toolArguments the arguments for the tool call
   * @return McpClientOperation instance for calling the specified tool with the provided arguments
   */
  public static McpClientOperation callTool(String toolName, Map<String, Object> toolArguments) {
    return McpClientOperation.of(
        McpClientOperation.McpMethod.CALL_TOOL.methodName(),
        Map.of(
            "name", toolName,
            "arguments", toolArguments));
  }
}
