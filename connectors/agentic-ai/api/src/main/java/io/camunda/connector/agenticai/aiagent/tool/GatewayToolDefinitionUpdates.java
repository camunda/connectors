/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import java.util.List;

/**
 * Represents changes to gateway tool definitions, tracking which tools have been added or removed.
 *
 * @param added List of gateway tool definition names that have been added
 * @param removed List of gateway tool definition names that have been removed
 */
public record GatewayToolDefinitionUpdates(List<String> added, List<String> removed) {
  public GatewayToolDefinitionUpdates {
    if (added == null) {
      added = List.of();
    }

    if (removed == null) {
      removed = List.of();
    }
  }

  public static GatewayToolDefinitionUpdates empty() {
    return new GatewayToolDefinitionUpdates(List.of(), List.of());
  }

  public boolean isEmpty() {
    return added.isEmpty() && removed.isEmpty();
  }
}
