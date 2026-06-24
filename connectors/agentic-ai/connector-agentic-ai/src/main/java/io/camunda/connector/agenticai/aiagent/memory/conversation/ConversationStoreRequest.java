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
import org.jspecify.annotations.Nullable;

/** Request to store a conversation via {@link ConversationSession#storeMessages}. */
public record ConversationStoreRequest(
    List<Message> messages, @Nullable DocumentRegistry documentRegistry) {

  public ConversationStoreRequest {
    Objects.requireNonNull(messages, "messages must not be null");
    messages = List.copyOf(messages);
    documentRegistry = documentRegistry != null ? documentRegistry : DocumentRegistry.empty();
  }

  public static ConversationStoreRequest of(List<Message> messages) {
    return new ConversationStoreRequest(messages, null);
  }

  public static ConversationStoreRequest of(
      List<Message> messages, DocumentRegistry documentRegistry) {
    return new ConversationStoreRequest(messages, documentRegistry);
  }
}
