/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.List;
import java.util.Objects;

/** Result of loading a conversation from a {@link ConversationSession}. */
public record ConversationLoadResult(List<Message> messages, DocumentRegistry documentRegistry) {

  public ConversationLoadResult {
    Objects.requireNonNull(messages, "messages must not be null");
    messages = List.copyOf(messages);
    documentRegistry = documentRegistry != null ? documentRegistry : DocumentRegistry.empty();
  }

  public static ConversationLoadResult of(List<Message> messages) {
    return new ConversationLoadResult(messages, DocumentRegistry.empty());
  }

  public static ConversationLoadResult of(
      List<Message> messages, DocumentRegistry documentRegistry) {
    return new ConversationLoadResult(messages, documentRegistry);
  }

  public static ConversationLoadResult empty() {
    return new ConversationLoadResult(List.of(), DocumentRegistry.empty());
  }
}
