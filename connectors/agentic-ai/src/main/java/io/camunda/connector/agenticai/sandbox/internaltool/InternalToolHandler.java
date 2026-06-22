/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;

/**
 * Handles a single internal tool. Implementations hold their own collaborators (e.g. skill
 * registry, document factory) as constructor fields and are registered in an {@link
 * InternalToolRegistry}.
 */
public interface InternalToolHandler {

  /**
   * Returns the LLM-facing tool name this handler services (must match an entry in {@link
   * InternalToolNames}).
   */
  String name();

  /** Returns the tool definition exposed to the LLM. */
  ToolDefinition definition();

  /**
   * Executes the tool call against the given sandbox session. Implementations must NOT throw — all
   * errors are returned as result content so the LLM can read them.
   */
  ToolCallResult execute(ToolCall toolCall, SandboxSession session);
}
