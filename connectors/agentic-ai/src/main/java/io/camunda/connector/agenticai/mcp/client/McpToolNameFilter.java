/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientToolsConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class McpToolNameFilter implements Predicate<String> {

  private final List<String> included;
  private final List<String> excluded;

  private McpToolNameFilter(List<String> included, List<String> excluded) {
    this.included = included;
    this.excluded = excluded;
  }

  @Override
  public boolean test(String toolName) {
    if (included.isEmpty()) {
      return !excluded.contains(toolName);
    }

    return included.contains(toolName) && !excluded.contains(toolName);
  }

  @Override
  public String toString() {
    return "McpToolNameFilter{" + "included=" + included + ", excluded=" + excluded + '}';
  }

  public static McpToolNameFilter from(McpClientToolsConfiguration toolsConfiguration) {
    final var included =
        Optional.ofNullable(toolsConfiguration)
            .map(McpClientToolsConfiguration::included)
            .orElseGet(Collections::emptyList);

    final var excluded =
        Optional.ofNullable(toolsConfiguration)
            .map(McpClientToolsConfiguration::excluded)
            .orElseGet(Collections::emptyList);

    return new McpToolNameFilter(included, excluded);
  }
}
