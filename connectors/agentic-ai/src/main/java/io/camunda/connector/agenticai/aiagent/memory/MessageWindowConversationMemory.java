/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import java.util.ArrayList;
import java.util.List;

public class MessageWindowConversationMemory extends AbstractConversationMemory
    implements ConversationMemory {

  private final int maxMessages;
  private final ArrayList<Message> messages = new ArrayList<>();
  private List<Message> filteredMessages;

  public MessageWindowConversationMemory(int maxMessages) {
    if (maxMessages < 0) {
      throw new IllegalArgumentException(
          "maxMessages must be greater than zero (was %d)".formatted(maxMessages));
    }

    this.maxMessages = maxMessages;
  }

  @Override
  public void addMessage(Message message) {
    addMessageWithSystemMessageSupport(messages, message);
    filteredMessages = null;
  }

  @Override
  public void addMessages(List<Message> messages) {
    messages.forEach(this::addMessage);
    filteredMessages = null;
  }

  @Override
  public List<Message> allMessages() {
    return List.copyOf(messages);
  }

  @Override
  public List<Message> filteredMessages() {
    if (filteredMessages == null) {
      filteredMessages = filteredMessages(messages, maxMessages);
    }

    return filteredMessages;
  }

  @Override
  public void clear() {
    messages.clear();
    filteredMessages = null;
  }

  // original implementation see Langchain4j
  private static List<Message> filteredMessages(List<Message> messages, int maxMessages) {
    final var filtered = new ArrayList<>(messages);
    while (filtered.size() > maxMessages) {
      int messageToEvictIndex = 0;

      // don't remove the system message
      if (filtered.getFirst() instanceof SystemMessage) {
        messageToEvictIndex = 1;
      }

      // remove the message at the current index
      Message evictedMessage = filtered.remove(messageToEvictIndex);

      // remove follow-up tool call results if existing as some LLM providers return an error when
      // receiving tool call results without the original tool call request
      if (evictedMessage instanceof AssistantMessage assistantMessage
          && assistantMessage.hasToolCallRequests()) {
        while (filtered.size() > messageToEvictIndex
            && filtered.get(messageToEvictIndex) instanceof ToolCallResultMessage) {
          filtered.remove(messageToEvictIndex);
        }
      }
    }

    return List.copyOf(filtered);
  }
}
