/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Holds a fully qualified MCP tool definition name (including the BPMN element ID + the MCP tool
 * call).
 *
 * <p>For example, a tool call with the BPMN element ID "myElement" and the MCP tool name
 * "myMcpTool" would be represented as: "MCP_myElement___myMcpTool". This tool name is passed to the
 * LLM as a unique tool name.
 *
 * <p>When collecting the tool calls to route to the ad-hoc sub-process, the transforming logic will
 * extract the BPMN element ID and include only the ID (e.g. "myElement") in the tool call name
 * while passing the MCP tool name as part of the operation payload, together with the arguments to
 * pass to the MCP tool.
 */
public record McpToolCallIdentifier(String elementName, String mcpToolName) {
  public static final String MCP_PREFIX = "MCP_";
  public static final String MCP_NAMESPACE_SEPARATOR = "___";

  private static final Pattern MCP_TOOL_CALL_PATTERN =
      Pattern.compile(
          "^"
              + MCP_PREFIX
              + "(?<elementName>.+)"
              + MCP_NAMESPACE_SEPARATOR
              + "(?<mcpToolName>.+)$");

  public String fullyQualifiedName() {
    return MCP_PREFIX + elementName + MCP_NAMESPACE_SEPARATOR + mcpToolName;
  }

  public static boolean isMcpToolCallIdentifier(String toolCallName) {
    return !StringUtils.isBlank(toolCallName)
        && MCP_TOOL_CALL_PATTERN.matcher(toolCallName).matches();
  }

  public static McpToolCallIdentifier fromToolCallName(String toolCallName) {
    if (!toolCallName.startsWith(MCP_PREFIX)) {
      throw invalidToolCallNameException(toolCallName);
    }

    final var parts =
        Arrays.stream(toolCallName.substring(MCP_PREFIX.length()).split(MCP_NAMESPACE_SEPARATOR))
            .toList()
            .stream()
            .map(String::trim)
            .toList();

    if (parts.size() != 2
        || StringUtils.isBlank(parts.get(0))
        || StringUtils.isBlank(parts.get(1))) {
      throw invalidToolCallNameException(toolCallName);
    }

    return new McpToolCallIdentifier(parts.get(0), parts.get(1));
  }

  private static IllegalArgumentException invalidToolCallNameException(String toolCallName) {
    return new IllegalArgumentException(
        "Failed to parse MCP tool call identifier from '%s'".formatted(toolCallName));
  }
}
