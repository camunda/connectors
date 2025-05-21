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

  public MessageWindowConversationMemory(int maxMessages) {
    if (maxMessages < 0) {
      throw new IllegalArgumentException(
          "maxMessages must be greater than zero (was %d)".formatted(maxMessages));
    }

    this.maxMessages = maxMessages;
  }

  @Override
  public void addMessage(Message message) {
    internalAddMessage(message);
    ensureCapacity();
  }

  @Override
  public void addMessages(List<Message> messages) {
    messages.forEach(this::internalAddMessage);
    ensureCapacity();
  }

  private void internalAddMessage(Message message) {
    addMessageWithSystemMessageSupport(messages, message);
  }

  @Override
  public List<Message> messages() {
    return List.copyOf(messages);
  }

  @Override
  public void clear() {
    messages.clear();
  }

  // original implementation see Langchain4j
  private void ensureCapacity() {
    while (messages.size() > maxMessages) {
      int messageToEvictIndex = 0;

      // don't remove the system message
      if (messages.getFirst() instanceof SystemMessage) {
        messageToEvictIndex = 1;
      }

      // remove the message at the current index
      Message evictedMessage = messages.remove(messageToEvictIndex);

      // remove follow-up tool call results if existing as some LLM providers return an error when
      // receiving tool call results without the original tool call request
      if (evictedMessage instanceof AssistantMessage assistantMessage
          && assistantMessage.hasToolCallRequests()) {
        while (messages.size() > messageToEvictIndex
            && messages.get(messageToEvictIndex) instanceof ToolCallResultMessage) {
          messages.remove(messageToEvictIndex);
        }
      }
    }
  }
}
