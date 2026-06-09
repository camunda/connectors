/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;

/**
 * Transient domain aggregate representing the agent's conversation state for one turn.
 *
 * <p>{@code context} and {@code runtimeMemory} are <em>restored</em> from persisted state at the
 * start of each turn. {@code toolCallResults} is <em>this turn's new engine input</em>, primed to
 * be folded into the conversation by the message handler.
 */
public final class AgentConversation {

  private AgentContext context;
  private final RuntimeMemory runtimeMemory;
  private final List<ToolCallResult> toolCallResults;

  private AgentConversation(
      AgentContext context, RuntimeMemory runtimeMemory, List<ToolCallResult> toolCallResults) {
    this.context = context;
    this.runtimeMemory = runtimeMemory;
    this.toolCallResults = toolCallResults;
  }

  /**
   * Builds the aggregate from persisted state and this turn's engine input.
   *
   * @param context the durable agent context restored from the process variable
   * @param messageMemory runtime memory restored from the conversation store
   * @param toolCallResults this turn's new tool-call results from the engine, to be folded in
   * @return a rehydrated {@code AgentConversation}
   */
  public static AgentConversation rehydrate(
      AgentContext context, RuntimeMemory messageMemory, List<ToolCallResult> toolCallResults) {
    return new AgentConversation(context, messageMemory, toolCallResults);
  }

  public AgentContext context() {
    return context;
  }

  public void updateContext(AgentContext newContext) {
    this.context = newContext;
  }

  public AgentState state() {
    return context.state();
  }

  public List<ToolDefinition> toolDefinitions() {
    return context.toolDefinitions();
  }

  public RuntimeMemory runtimeMemory() {
    return runtimeMemory;
  }

  public List<ToolCallResult> toolCallResults() {
    return toolCallResults;
  }
}
