/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.function.Predicate;

/** The {@link LocalTool} that {@link LocalTool#builder(String)} builds. */
final class DefaultLocalTool implements LocalTool {

  private final ToolDefinition definition;
  private final Predicate<AgentConfiguration> availability;
  private final LocalTool.Handler handler;

  DefaultLocalTool(
      ToolDefinition definition,
      Predicate<AgentConfiguration> availability,
      LocalTool.Handler handler) {
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
  public LocalToolResult call(LocalToolCall call, LocalToolContext context) {
    return handler.call(call, context);
  }
}
