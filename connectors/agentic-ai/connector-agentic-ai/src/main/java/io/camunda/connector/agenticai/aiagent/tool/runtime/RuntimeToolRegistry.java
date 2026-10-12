/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The runtime tools registered as beans, and which of them an agent offers. An agent instance
 * created without the runtime tools feature (see {@code AgentContext#FEATURE_RUNTIME_TOOLS}) offers
 * no runtime tool.
 */
public interface RuntimeToolRegistry {

  /**
   * The names of all registered tools, whether available or not; a tool of the process must not use
   * one of them.
   */
  Set<String> toolNames();

  /** The tools available for the agent of {@code configuration}, in registration order. */
  List<RuntimeTool> availableTools(AgentConfiguration configuration);

  /** The tool of the given name, if it is registered and available for {@code configuration}. */
  Optional<RuntimeTool> findAvailable(String name, AgentConfiguration configuration);

  /** The definitions of the {@link #availableTools available tools}. */
  default List<ToolDefinition> availableToolDefinitions(AgentConfiguration configuration) {
    return availableTools(configuration).stream().map(RuntimeTool::definition).toList();
  }
}
