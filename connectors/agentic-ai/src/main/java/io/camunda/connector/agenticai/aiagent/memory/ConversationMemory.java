/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/**
 * The loaded conversation memory containing all messages. Can optionally apply filters to the
 * messages before passing them back to the LLM.
 */
public interface ConversationMemory {
  void addMessages(List<Message> messages);

  void addMessage(Message message);

  /** All messages which should be stored. */
  List<Message> allMessages();

  /** Filtered message view which should be included in the LLM request. */
  default List<Message> filteredMessages() {
    return allMessages();
  }

  void clear();
}
