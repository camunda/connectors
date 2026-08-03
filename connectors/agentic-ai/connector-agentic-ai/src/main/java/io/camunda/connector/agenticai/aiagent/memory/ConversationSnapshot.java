/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the windowed conversation sent to the LLM. Carries the filtered message
 * list and the tool definitions for this invocation.
 */
public record ConversationSnapshot(List<Message> messages, List<ToolDefinition> toolDefinitions) {

  public ConversationSnapshot {
    Objects.requireNonNull(messages);
    Objects.requireNonNull(toolDefinitions);
    messages = List.copyOf(messages);
    toolDefinitions = List.copyOf(toolDefinitions);
  }
}
