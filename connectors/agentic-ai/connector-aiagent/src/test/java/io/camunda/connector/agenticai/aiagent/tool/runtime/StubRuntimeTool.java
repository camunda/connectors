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

public record StubRuntimeTool(
    ToolDefinition definition, Predicate<RuntimeToolContext> enabled, Handler handler)
    implements RuntimeTool {

  @FunctionalInterface
  public interface Handler {
    RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call);
  }

  public static StubRuntimeTool of(String name, Handler handler) {
    return of(ToolDefinition.runtime(name, null, Map.of("type", "object")), handler);
  }

  public static StubRuntimeTool of(ToolDefinition definition, Handler handler) {
    return new StubRuntimeTool(definition, context -> true, handler);
  }

  public StubRuntimeTool enabledWhen(Predicate<RuntimeToolContext> enabled) {
    return new StubRuntimeTool(definition, enabled, handler);
  }

  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return enabled.test(context);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call) {
    return handler.call(context, call);
  }
}
