/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;

public interface ToolCallConverter {
  ToolExecutionRequest asToolExecutionRequest(ToolCall toolCall);

  ToolCall asToolCall(ToolExecutionRequest toolExecutionRequest);

  ToolExecutionResultMessage asToolExecutionResultMessage(ToolCallResult toolCallResult);
}
