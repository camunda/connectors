/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record McpClientMessage(String method, Map<String, Object> params) {

  public static final String METHOD_TOOLS_LIST = "tools/list";
  public static final String METHOD_TOOLS_CALL = "tools/call";

  public static McpClientMessage listTools() {
    return new McpClientMessage(METHOD_TOOLS_LIST, Map.of());
  }

  public static McpClientMessage callTool(String toolName, Map<String, Object> arguments) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", toolName);
    params.put("arguments", arguments);

    return new McpClientMessage(METHOD_TOOLS_CALL, params);
  }
}
