/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public record A2aToolCallIdentifier(String elementName) {
  public static final String A2A_PREFIX = "A2A_";

  private static final Pattern A2A_TOOL_CALL_PATTERN =
      Pattern.compile("^" + A2A_PREFIX + "(?<elementName>\\S+)");

  public String fullyQualifiedName() {
    return A2A_PREFIX + elementName;
  }

  public static boolean isA2aToolCallIdentifier(String toolCallName) {
    return !StringUtils.isBlank(toolCallName)
        && A2A_TOOL_CALL_PATTERN.matcher(toolCallName).matches();
  }

  public static A2aToolCallIdentifier fromToolCallName(String toolCallName) {
    if (!isA2aToolCallIdentifier(toolCallName)) {
      throw new IllegalArgumentException(
          "Invalid A2A tool call name: '%s'".formatted(toolCallName));
    }

    return new A2aToolCallIdentifier(toolCallName.substring(A2A_PREFIX.length()));
  }
}
