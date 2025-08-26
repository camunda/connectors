/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;
import java.util.Optional;

/**
 * Runtime memory interface for storing and retrieving messages during agent execution.
 *
 * <p>A @{@link ConversationStore} is responsible to load messages from a {@link
 * ConversationContext} into the runtime memory on entering the agent and to store messages from the
 * runtime memory into the {@link ConversationContext} before exiting the agent.
 */
public interface RuntimeMemory {
  void addMessage(Message message);

  default void addMessages(List<Message> messages) {
    messages.forEach(this::addMessage);
  }

  /** All messages which should be stored. */
  List<Message> allMessages();

  /** Filtered message view which should be included in the LLM request. */
  default List<Message> filteredMessages() {
    return allMessages();
  }

  default Optional<Message> lastMessage() {
    final var messages = allMessages();
    if (messages.isEmpty()) {
      return Optional.empty();
    }

    return Optional.ofNullable(messages.getLast());
  }

  void clear();
}
