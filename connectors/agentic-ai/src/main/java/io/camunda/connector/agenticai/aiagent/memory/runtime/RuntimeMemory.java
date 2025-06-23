/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/**
 * Runtime memory interface for storing and retrieving messages during agent execution.
 *
 * <p>A @{@link io.camunda.connector.agenticai.aiagent.memory.ConversationStore} is responsible to
 * load messages from a {@link io.camunda.connector.agenticai.aiagent.memory.ConversationContext}
 * into the runtime memory on entering the agent and to store messages from the runtime memory into
 * the {@link io.camunda.connector.agenticai.aiagent.memory.ConversationContext} before exiting the
 * agent.
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

  void clear();
}
