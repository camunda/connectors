/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the runtime tools registered as beans. An agent instance created without the runtime
 * tools feature offers none.
 */
public class RuntimeToolRegistryImpl implements RuntimeToolRegistry {

  private final Map<String, RuntimeTool> toolsByName = new LinkedHashMap<>();

  /**
   * @throws IllegalStateException if two tools have the same name or a name is blank
   */
  public RuntimeToolRegistryImpl(List<RuntimeTool> tools) {
    for (final var tool : tools) {
      final var name = tool.definition().name();
      if (name.isBlank()) {
        throw new IllegalStateException("A runtime tool must have a non-blank name");
      }
      if (toolsByName.putIfAbsent(name, tool) != null) {
        throw new IllegalStateException(
            "More than one runtime tool is named '%s'; runtime tool names must be unique."
                .formatted(name));
      }
    }
  }

  @Override
  public Set<String> toolNames() {
    return Set.copyOf(toolsByName.keySet());
  }

  @Override
  public List<RuntimeTool> availableTools(AgentConfiguration configuration) {
    if (!configuration.supportsRuntimeTools()) {
      return List.of();
    }
    return toolsByName.values().stream().filter(tool -> tool.isAvailable(configuration)).toList();
  }

  @Override
  public Optional<RuntimeTool> findAvailable(String name, AgentConfiguration configuration) {
    if (!configuration.supportsRuntimeTools()) {
      return Optional.empty();
    }
    return Optional.ofNullable(toolsByName.get(name))
        .filter(tool -> tool.isAvailable(configuration));
  }
}
