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
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;

/**
 * The runtime tools registered as beans: offers the enabled ones to the model and executes their
 * calls, the one place a {@link RuntimeTool} is called. An agent instance created without the
 * runtime tools feature (see {@code AgentFeatures#RUNTIME_TOOLS}) has none; for every other agent a
 * tool is offered while it is {@link RuntimeTool#isEnabled enabled}.
 */
public interface RuntimeTools {

  /** The definitions of the enabled tools in registration order, empty without the feature. */
  List<ToolDefinition> enabledDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Executes the calls, which must be calls of {@link
   * io.camunda.connector.agenticai.aiagent.model.tool.ToolKind#RUNTIME runtime tools}, and returns
   * one execution per call in call order.
   *
   * <p>A call of a tool that does not exist or is not enabled when it is executed gets an error
   * result, and so does a {@link
   * io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException}. Any other exception
   * propagates.
   */
  List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext, AgentContext agentContext, List<ToolCall> calls);
}
