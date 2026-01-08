/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.filters;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;

@AgenticAiRecord
public record AllowDenyList(List<String> allowed, List<String> denied) {

  public static AllowDenyList allowingEverything() {
    return new AllowDenyList(List.of(), List.of());
  }

  public boolean isPassing(String toolName) {
    return (allowed.isEmpty() || allowed.contains(toolName)) && !denied.contains(toolName);
  }

  @Override
  public String toString() {
    return "[" + "allowed=" + allowed + ", denied=" + denied + ']';
  }
}
