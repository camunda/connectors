/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;

/** Executes the calls of runtime tools: the one place a {@link RuntimeTool} runs. */
public interface RuntimeToolExecutor {

  /**
   * Executes the calls, which must be calls of {@link
   * io.camunda.connector.agenticai.aiagent.model.tool.ToolKind#RUNTIME runtime tools}, and returns
   * one execution per call in call order.
   *
   * <p>A call of a tool that is not enabled when it runs, or not registered, gets an error result,
   * and so does a {@link io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException}. Any
   * other exception propagates.
   */
  List<RuntimeToolExecution> execute(RuntimeToolContext context, List<ToolCall> runtimeToolCalls);
}
