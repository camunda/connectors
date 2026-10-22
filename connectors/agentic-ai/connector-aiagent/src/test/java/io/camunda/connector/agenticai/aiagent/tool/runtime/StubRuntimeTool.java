/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.Map;
import java.util.function.Predicate;

/** A {@link RuntimeTool} with a given definition, enabled check and call, for tests. */
public record StubRuntimeTool(
    ToolDefinition definition, Predicate<RuntimeToolContext> enabled, Handler handler)
    implements RuntimeTool {

  /** What the stub does when it is called. */
  @FunctionalInterface
  public interface Handler {
    RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context);
  }

  /** A tool without parameters that is always enabled. */
  public static StubRuntimeTool of(String name, Handler handler) {
    return of(
        ToolDefinition.builder().name(name).inputSchema(Map.of("type", "object")).build(), handler);
  }

  /** A tool with the given definition that is always enabled. */
  public static StubRuntimeTool of(ToolDefinition definition, Handler handler) {
    return new StubRuntimeTool(definition, context -> true, handler);
  }

  /** The tool, enabled only for the agents that satisfy {@code enabled}. */
  public StubRuntimeTool enabledWhen(Predicate<RuntimeToolContext> enabled) {
    return new StubRuntimeTool(definition, enabled, handler);
  }

  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return enabled.test(context);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    return handler.call(call, context);
  }
}
