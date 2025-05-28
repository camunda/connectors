/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

public interface GatewayToolCallTransformer {
  List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls);

  List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);
}
