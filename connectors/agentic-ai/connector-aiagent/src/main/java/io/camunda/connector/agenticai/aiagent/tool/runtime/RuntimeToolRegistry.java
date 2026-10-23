/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The runtime tools registered as beans, and which of them an agent offers. An agent instance
 * created without the runtime tools feature (see {@code AgentFeatures#RUNTIME_TOOLS}) offers no
 * runtime tool; for every other agent a tool is offered while it is {@link RuntimeTool#isEnabled
 * enabled}.
 */
public interface RuntimeToolRegistry {

  /**
   * The names of all registered tools, whether enabled or not; a tool of the process must not use
   * one of them.
   */
  Set<String> toolNames();

  /**
   * The tools enabled for the agent execution, in registration order.
   *
   * @param executionContext the invocation of the agent
   * @param agentContext the persisted state of the agent
   */
  List<RuntimeTool> enabledTools(AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * The tool of the given name, if it is registered and enabled for the agent execution.
   *
   * @param executionContext the invocation of the agent
   * @param agentContext the persisted state of the agent
   * @param name the name of the tool
   */
  Optional<RuntimeTool> findEnabled(
      AgentExecutionContext executionContext, AgentContext agentContext, String name);

  /**
   * The kind of tool the given name stands for: {@link ToolKind#RUNTIME} if the agent supports the
   * runtime tools feature and the name belongs to a registered tool, whether it is enabled or not,
   * otherwise {@link ToolKind#PROCESS}.
   *
   * @param agentContext the persisted state of the agent
   * @param toolName the name of the tool
   */
  ToolKind kindOf(AgentContext agentContext, String toolName);

  /**
   * Tags every tool call of the message with the {@link #kindOf kind} of its tool. Returns {@code
   * message} itself when no call changes.
   */
  default AssistantMessage tagToolCalls(AgentContext agentContext, AssistantMessage message) {
    if (!message.hasToolCalls()) {
      return message;
    }
    boolean changed = false;
    final var tagged = new ArrayList<ToolCall>();
    for (final var toolCall : message.toolCalls()) {
      final var kind = kindOf(agentContext, toolCall.name());
      changed |= kind != toolCall.kind();
      tagged.add(kind == toolCall.kind() ? toolCall : toolCall.withKind(kind));
    }
    return changed ? message.withToolCalls(List.copyOf(tagged)) : message;
  }

  /** The definitions of the {@link #enabledTools enabled tools}. */
  default List<ToolDefinition> enabledToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    return enabledTools(executionContext, agentContext).stream()
        .map(RuntimeTool::definition)
        .toList();
  }
}
