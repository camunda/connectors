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
 * LocalToolCallHandler} for how the answered calls and the remaining calls of a batch are
 * processed.
 *
 * <p>To add a locally handled tool (for example loading a skill or saving a memory), implement this
 * interface, return {@link ToolCallVerdict.Answer} with the result content for the tool's calls and
 * {@link ToolCallVerdict.Pass} for every other call, and register the implementation as a bean. A
 * verdict concerns the single call only, so the inspector needs no knowledge of the rest of the
 * batch: the call is answered whether the other calls of the batch are local, real or a mix. An
 * inspector must not have side effects beyond its {@code Answer}; the state a tool changes (such as
 * the documents selected for the response) is carried by the verdict.
 */
public interface ToolCallInspector {

  ToolCallVerdict inspect(ToolCall toolCall, LocalToolCallContext context);
}
