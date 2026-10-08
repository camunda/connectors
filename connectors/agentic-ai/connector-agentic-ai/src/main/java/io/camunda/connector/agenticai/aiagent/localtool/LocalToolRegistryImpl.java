/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.InternalToolNames;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the local tools registered as beans. Local tools belong to the documents feature: an
 * agent instance created without it offers none.
 */
public class LocalToolRegistryImpl implements LocalToolRegistry {

  private final Map<String, LocalTool> toolsByName = new LinkedHashMap<>();

  /**
   * @throws IllegalStateException if two tools have the same name, a name is blank or a name does
   *     not start with the reserved prefix {@link InternalToolNames#PREFIX}
   */
  public LocalToolRegistryImpl(List<LocalTool> tools) {
    for (final var tool : tools) {
      final var name = tool.definition().name();
      if (name.isBlank()) {
        throw new IllegalStateException("A local tool must have a non-blank name");
      }
      if (!InternalToolNames.isReserved(name)) {
        throw new IllegalStateException(
            "The local tool '%s' must be named in the reserved namespace, starting with '%s'."
                .formatted(name, InternalToolNames.PREFIX));
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
    if (!configuration.supportsDocuments()) {
      return List.of();
    }
    return toolsByName.values().stream().filter(tool -> tool.isAvailable(configuration)).toList();
  }

  @Override
  public Optional<ToolDefinition> findDefinition(String name) {
    return Optional.ofNullable(toolsByName.get(name)).map(LocalTool::definition);
  }

  @Override
  public Optional<LocalTool> findAvailable(String name, AgentConfiguration configuration) {
    if (!configuration.supportsDocuments()) {
      return Optional.empty();
    }
    return Optional.ofNullable(toolsByName.get(name))
        .filter(tool -> tool.isAvailable(configuration));
  }
}
