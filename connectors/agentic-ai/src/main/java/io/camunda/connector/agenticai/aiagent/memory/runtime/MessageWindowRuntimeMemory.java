/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Exposes a filtered view of the last-n messages in the conversation. Oldest messages are removed
 * first, while making sure to also remove orphaned tool call results.
 */
public class MessageWindowRuntimeMemory implements RuntimeMemory {

  private final RuntimeMemory delegate;
  private final int maxMessages;
  private List<Message> filteredMessages;

  public MessageWindowRuntimeMemory(int maxMessages) {
    this(new DefaultRuntimeMemory(), maxMessages);
  }

  public MessageWindowRuntimeMemory(RuntimeMemory delegate, int maxMessages) {
    if (maxMessages < 0) {
      throw new IllegalArgumentException(
          "maxMessages must be greater than zero (was %d)".formatted(maxMessages));
    }

    this.delegate = delegate;
    this.maxMessages = maxMessages;
  }

  @Override
  public void addMessage(Message message) {
    delegate.addMessage(message);
    filteredMessages = null;
  }

  @Override
  public void addMessages(List<Message> messages) {
    delegate.addMessages(messages);
    filteredMessages = null;
  }

  @Override
  public List<Message> allMessages() {
    return delegate.allMessages();
  }

  @Override
  public List<Message> filteredMessages() {
    if (filteredMessages == null) {
      filteredMessages = filteredMessages(delegate.allMessages(), maxMessages);
    }

    return filteredMessages;
  }

  @Override
  public void clear() {
    delegate.clear();
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
          && assistantMessage.hasToolCalls()) {
        while (filtered.size() > messageToEvictIndex
            && filtered.get(messageToEvictIndex) instanceof ToolCallResultMessage) {
          filtered.remove(messageToEvictIndex);
        }
      }
    }

    return List.copyOf(filtered);
  }
}
