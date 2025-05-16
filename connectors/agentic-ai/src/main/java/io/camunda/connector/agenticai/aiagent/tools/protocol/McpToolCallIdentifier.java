/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools.protocol;

import org.apache.commons.lang3.StringUtils;

/**
 * Holds a fully qualified MCP tool definition name (including the BPMN element ID + the MCP tool
 * call)
 */
public record McpToolCallIdentifier(String elementName, String mcpToolName) {
  public static final String MCP_PREFIX = "MCP_";
  public static final String MCP_NAMESPACE_SEPARATOR = "___";

  public String fullyQualifiedName() {
    return MCP_PREFIX + elementName + MCP_NAMESPACE_SEPARATOR + mcpToolName;
  }

  public static boolean isMcpToolCallIdentifier(String toolCallName) {
    return toolCallName.startsWith(MCP_PREFIX) && toolCallName.contains(MCP_NAMESPACE_SEPARATOR);
  }

  public static McpToolCallIdentifier fromToolCallName(String toolCallName) {
    String[] parts = toolCallName.substring(MCP_PREFIX.length()).split(MCP_NAMESPACE_SEPARATOR);

    if (parts.length != 2 && StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
      throw new IllegalArgumentException(
          "Failed to parse MCP tool call identifier from '%s'".formatted(toolCallName));
    }

    return new McpToolCallIdentifier(parts[0], parts[1]);
  }
}
