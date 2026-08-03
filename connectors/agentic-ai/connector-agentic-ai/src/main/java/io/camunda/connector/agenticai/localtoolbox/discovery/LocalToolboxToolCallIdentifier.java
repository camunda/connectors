/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.discovery;

import java.util.regex.Pattern;

/**
 * Holds a fully qualified local toolbox tool definition name (including the BPMN element ID of the
 * gateway element in the calling AHSP + the tool name discovered inside the toolbox process).
 *
 * <p>For example, a gateway element with BPMN element ID "myToolbox" exposing a discovered tool
 * named "mySharedTool" is represented as: "LOCALTOOLBOX_myToolbox___mySharedTool". This is the name
 * passed to the LLM as a unique tool name, mirroring {@code McpToolCallIdentifier}.
 */
public record LocalToolboxToolCallIdentifier(String elementId, String toolName) {
  public static final String LOCAL_TOOLBOX_PREFIX = "LOCALTOOLBOX_";
  public static final String LOCAL_TOOLBOX_NAMESPACE_SEPARATOR = "___";

  private static final Pattern LOCAL_TOOLBOX_TOOL_CALL_PATTERN =
      Pattern.compile(
          "^"
              + LOCAL_TOOLBOX_PREFIX
              + "(?<elementId>\\S+?)"
              + LOCAL_TOOLBOX_NAMESPACE_SEPARATOR
              + "(?<toolName>\\S+)$");

  public String fullyQualifiedName() {
    return LOCAL_TOOLBOX_PREFIX + elementId + LOCAL_TOOLBOX_NAMESPACE_SEPARATOR + toolName;
  }

  public static boolean isLocalToolboxToolCallIdentifier(String toolCallName) {
    return toolCallName != null && LOCAL_TOOLBOX_TOOL_CALL_PATTERN.matcher(toolCallName).matches();
  }

  public static LocalToolboxToolCallIdentifier fromToolCallName(String toolCallName) {
    var matcher = LOCAL_TOOLBOX_TOOL_CALL_PATTERN.matcher(toolCallName);
    if (!matcher.matches()) {
      throw invalidToolCallNameException(toolCallName);
    }
    return new LocalToolboxToolCallIdentifier(
        matcher.group("elementId"), matcher.group("toolName"));
  }

  private static IllegalArgumentException invalidToolCallNameException(String toolCallName) {
    return new IllegalArgumentException(
        "Failed to parse local toolbox tool call identifier from '%s'".formatted(toolCallName));
  }
}
