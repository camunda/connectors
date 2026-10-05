/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LocalToolRegistryImpl implements LocalToolRegistry {

  private final Map<String, LocalTool> toolsByName = new LinkedHashMap<>();

  /**
   * @throws IllegalStateException if two tools have the same name or a name is blank
   */
  public LocalToolRegistryImpl(List<LocalTool> tools) {
    for (final var tool : tools) {
      final var name = tool.definition().name();
      if (name.isBlank()) {
        throw new IllegalStateException("A local tool must have a non-blank name");
      }
      if (toolsByName.putIfAbsent(name, tool) != null) {
        throw new IllegalStateException(
            "More than one local tool is named '%s'; local tool names must be unique."
                .formatted(name));
      }
    }
  }

  @Override
  public Set<String> toolNames() {
    return Set.copyOf(toolsByName.keySet());
  }

  @Override
  public List<LocalTool> availableTools(AgentConfiguration configuration) {
    return toolsByName.values().stream().filter(tool -> tool.isAvailable(configuration)).toList();
  }

  @Override
  public Optional<LocalTool> findAvailable(String name, AgentConfiguration configuration) {
    return Optional.ofNullable(toolsByName.get(name))
        .filter(tool -> tool.isAvailable(configuration));
  }
}
