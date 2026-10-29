/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.conversation;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Objects;

/**
 * The context window of one model call: the messages the model reads, the tool definitions it is
 * offered and the documents of the windowed messages. The documents are what the runtime tools the
 * model calls from this window can access; they hold the full document references and never reach
 * the model, which gets a {@link ConversationSnapshot#of snapshot} of the window.
 *
 * @param messages the windowed messages, as the model reads them
 * @param toolDefinitions the tool definitions of the invocation
 * @param documents the documents of the windowed messages
 */
public record ConversationWindow(
    List<Message> messages, List<ToolDefinition> toolDefinitions, RuntimeToolDocuments documents) {

  public ConversationWindow {
    Objects.requireNonNull(messages);
    Objects.requireNonNull(toolDefinitions);
    Objects.requireNonNull(documents);
    messages = List.copyOf(messages);
    toolDefinitions = List.copyOf(toolDefinitions);
  }
}
