/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RuntimeToolRegistryImpl implements RuntimeToolRegistry {

  private record Registration(RuntimeTool tool, ToolDefinition definition) {}

  private final Map<String, Registration> registrationsByName = new LinkedHashMap<>();

  /**
   * @throws IllegalStateException if two tools have the same name, a name is blank or a definition
   *     is not of kind {@link ToolKind#RUNTIME}
   */
  public RuntimeToolRegistryImpl(List<RuntimeTool> tools) {
    for (final var tool : tools) {
      final var definition = tool.definition();
      final var name = definition.name();
      if (name.isBlank()) {
        throw new IllegalStateException("A runtime tool must have a non-blank name");
      }
      if (definition.kind() != ToolKind.RUNTIME) {
        throw new IllegalStateException(
            "The runtime tool '%s' must declare the kind %s in its definition."
                .formatted(name, ToolKind.RUNTIME));
      }
      if (registrationsByName.putIfAbsent(name, new Registration(tool, definition)) != null) {
        throw new IllegalStateException(
            "More than one runtime tool is named '%s'; runtime tool names must be unique."
                .formatted(name));
      }
    }
  }

  @Override
  public Set<String> toolNames() {
    return Set.copyOf(registrationsByName.keySet());
  }

  @Override
  public List<ToolDefinition> enabledToolDefinitions(RuntimeToolContext context) {
    if (!context.agentContext().supports(AgentFeatures.RUNTIME_TOOLS)) {
      return List.of();
    }
    return registrationsByName.values().stream()
        .filter(registration -> registration.tool().isEnabled(context))
        .map(Registration::definition)
        .toList();
  }

  @Override
  public Optional<RuntimeTool> findEnabled(RuntimeToolContext context, String name) {
    if (!context.agentContext().supports(AgentFeatures.RUNTIME_TOOLS)) {
      return Optional.empty();
    }
    return Optional.ofNullable(registrationsByName.get(name))
        .map(Registration::tool)
        .filter(tool -> tool.isEnabled(context));
  }
}
