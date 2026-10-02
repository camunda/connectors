/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

/** What a {@link ToolCallInspector} decides about a single tool call. */
public sealed interface ToolCallVerdict {

  /** The call is not the inspector's concern; it proceeds to the process like any other. */
  record Pass() implements ToolCallVerdict {}

  /** The call is answered by the agent with {@code content} instead of running it. */
  record Answer(Object content) implements ToolCallVerdict {}
}
