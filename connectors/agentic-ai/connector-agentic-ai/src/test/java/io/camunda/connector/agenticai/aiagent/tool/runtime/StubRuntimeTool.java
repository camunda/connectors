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
import java.util.Map;
import java.util.function.BiPredicate;

/** A {@link RuntimeTool} with a given definition, availability and call, for tests. */
public record StubRuntimeTool(
    ToolDefinition definition,
    BiPredicate<AgentExecutionContext, AgentContext> availability,
    Handler handler)
    implements RuntimeTool {

  /** What the stub does when it is called. */
  @FunctionalInterface
  public interface Handler {
    RuntimeToolResult call(
        RuntimeToolCall call,
        AgentExecutionContext executionContext,
        RuntimeToolConversation conversation);
  }

  /** A tool without parameters that is always available. */
  public static StubRuntimeTool of(String name, Handler handler) {
    return of(
        ToolDefinition.builder().name(name).inputSchema(Map.of("type", "object")).build(), handler);
  }

  /** A tool with the given definition that is always available. */
  public static StubRuntimeTool of(ToolDefinition definition, Handler handler) {
    return new StubRuntimeTool(definition, (executionContext, agentContext) -> true, handler);
  }

  /** The tool, available only for the agent executions that satisfy {@code availability}. */
  public StubRuntimeTool availableWhen(
      BiPredicate<AgentExecutionContext, AgentContext> availability) {
    return new StubRuntimeTool(definition, availability, handler);
  }

  @Override
  public boolean isAvailable(AgentExecutionContext executionContext, AgentContext agentContext) {
    return availability.test(executionContext, agentContext);
  }

  @Override
  public RuntimeToolResult call(
      RuntimeToolCall call,
      AgentExecutionContext executionContext,
      RuntimeToolConversation conversation) {
    return handler.call(call, executionContext, conversation);
  }
}
