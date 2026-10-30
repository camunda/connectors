/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;

/**
 * Executes the calls of runtime tools: the one place a {@link RuntimeTool} runs.
 *
 * <p>The calls of one assistant message may run concurrently, and their executions are returned in
 * the order of the calls. Every execution keeps the time the call started and completed, so metrics
 * and events with durations can be attached here.
 */
public interface RuntimeToolExecutor {

  /**
   * Executes the calls, which must be calls of {@link
   * io.camunda.connector.agenticai.aiagent.model.tool.ToolKind#RUNTIME runtime tools}.
   *
   * <p>A call of a tool that is not enabled when it runs, or that is not registered, gets an error
   * result. A {@link io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException} gives
   * the call an error result carrying its message. Any other exception propagates.
   *
   * @param executionContext the invocation of the agent
   * @param agentContext the persisted state of the agent
   * @param documents the documents the agent knows at the start of the calls
   * @param runtimeToolCalls the calls to execute
   * @return one execution per call, in the order of the calls
   */
  List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeToolDocuments documents,
      List<ToolCall> runtimeToolCalls);
}
