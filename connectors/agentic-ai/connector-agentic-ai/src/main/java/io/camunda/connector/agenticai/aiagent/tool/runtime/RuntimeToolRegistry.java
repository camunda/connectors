/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The runtime tools registered as beans, and which of them an agent offers. An agent instance
 * created without the runtime tools feature (see {@code AgentContext#FEATURE_RUNTIME_TOOLS}) offers
 * no runtime tool; for every other agent a tool is offered while it is {@link
 * RuntimeTool#isAvailable available}.
 */
public interface RuntimeToolRegistry {

  /**
   * The names of all registered tools, whether available or not; a tool of the process must not use
   * one of them.
   */
  Set<String> toolNames();

  /**
   * The tools available for the agent execution, in registration order.
   *
   * @param executionContext the invocation of the agent
   * @param agentContext the persisted state of the agent
   */
  List<RuntimeTool> availableTools(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * The tool of the given name, if it is registered and available for the agent execution.
   *
   * @param executionContext the invocation of the agent
   * @param agentContext the persisted state of the agent
   */
  Optional<RuntimeTool> findAvailable(
      String name, AgentExecutionContext executionContext, AgentContext agentContext);

  /** The definitions of the {@link #availableTools available tools}. */
  default List<ToolDefinition> availableToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    return availableTools(executionContext, agentContext).stream()
        .map(RuntimeTool::definition)
        .toList();
  }
}
