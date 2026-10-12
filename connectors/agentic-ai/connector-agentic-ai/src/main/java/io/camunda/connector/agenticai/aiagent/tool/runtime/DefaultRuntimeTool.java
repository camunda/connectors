/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.function.Predicate;

/** The {@link RuntimeTool} that {@link RuntimeTool#builder(String)} builds. */
final class DefaultRuntimeTool implements RuntimeTool {

  private final ToolDefinition definition;
  private final Predicate<AgentConfiguration> availability;
  private final RuntimeTool.Handler handler;

  DefaultRuntimeTool(
      ToolDefinition definition,
      Predicate<AgentConfiguration> availability,
      RuntimeTool.Handler handler) {
    this.definition = definition;
    this.availability = availability;
    this.handler = handler;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public boolean isAvailable(AgentConfiguration configuration) {
    return availability.test(configuration);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    return handler.call(call, context);
  }
}
