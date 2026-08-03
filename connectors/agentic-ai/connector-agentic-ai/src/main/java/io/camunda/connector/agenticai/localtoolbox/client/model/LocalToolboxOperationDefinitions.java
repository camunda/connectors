/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client.model;

import java.util.Map;

/** Convenience factories for {@link LocalToolboxOperation} instances used by the framework. */
public class LocalToolboxOperationDefinitions {

  public static LocalToolboxOperation listTools() {
    return LocalToolboxOperation.of(
        LocalToolboxOperation.LocalToolboxMethod.LIST_TOOLS.methodName());
  }

  public static LocalToolboxOperation callTool(String toolName, Map<String, Object> toolArguments) {
    return LocalToolboxOperation.of(
        LocalToolboxOperation.LocalToolboxMethod.CALL_TOOL.methodName(),
        Map.of("name", toolName, "arguments", toolArguments));
  }

  private LocalToolboxOperationDefinitions() {}
}
