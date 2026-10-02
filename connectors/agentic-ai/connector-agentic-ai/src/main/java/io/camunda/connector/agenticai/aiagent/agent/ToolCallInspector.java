/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;

/**
 * Inspects one tool call of an assistant message before it is handed to the process. An inspector
 * either lets the call pass or answers it itself (a validation error, a built-in tool); see {@link
 * LocalToolCallHandler} for how the verdicts of a batch combine.
 */
public interface ToolCallInspector {

  ToolCallVerdict inspect(ToolCall toolCall, LocalToolCallContext context);
}
