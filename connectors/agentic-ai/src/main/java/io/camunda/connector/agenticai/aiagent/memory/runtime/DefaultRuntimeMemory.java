/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.ArrayList;
import java.util.List;

public class DefaultRuntimeMemory implements RuntimeMemory {
  private final ArrayList<Message> messages = new ArrayList<>();

  @Override
  public void addMessage(Message message) {
    addMessageWithSystemMessageSupport(messages, message);
  }

  @Override
  public List<Message> allMessages() {
    return List.copyOf(messages);
  }

  @Override
  public void clear() {
    messages.clear();
  }

  private void addMessageWithSystemMessageSupport(ArrayList<Message> messages, Message message) {
    if (message instanceof SystemMessage) {
      final var existingSystemMessage =
          messages.stream().filter(m -> m instanceof SystemMessage).findFirst().orElse(null);

      if (existingSystemMessage != null) {
        // replace system message if it already exists and the one to add is different
        if (!existingSystemMessage.equals(message)) {
          final var existingIndex = messages.indexOf(existingSystemMessage);
          messages.set(existingIndex, message);
        }
      } else {
        // add system message to the beginning of the conversation
        messages.add(0, message);
      }
    } else {
      messages.add(message);
    }
  }
}
