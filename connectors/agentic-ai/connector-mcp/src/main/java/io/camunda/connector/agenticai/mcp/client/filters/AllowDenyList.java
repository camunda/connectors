/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.filters;

import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import java.util.List;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
public record AllowDenyList(List<String> allowed, List<String> denied) {

  public static AllowDenyList allowingEverything() {
    return new AllowDenyList(List.of(), List.of());
  }

  /** Returns the list with the given names. A {@code null} list counts as an empty list. */
  public static AllowDenyList of(@Nullable List<String> allowed, @Nullable List<String> denied) {
    return AllowDenyListBuilder.builder().allowed(allowed).denied(denied).build();
  }

  public boolean isPassing(String toolName) {
    return (allowed.isEmpty() || allowed.contains(toolName)) && !denied.contains(toolName);
  }

  @Override
  public String toString() {
    return "[" + "allowed=" + allowed + ", denied=" + denied + ']';
  }
}
