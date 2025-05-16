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
