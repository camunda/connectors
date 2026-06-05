/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure windowing strategy that returns the last-N messages from a conversation, preserving the
 * system message and evicting orphaned tool-call results when their request is evicted.
 *
 * <p>This is the windowed view of the conversation sent to the model this turn — NOT a full copy.
 */
public final class SlidingMessagesWindow {

  public static final int DEFAULT_MAX_MESSAGES = 20;

  private final int maxMessages;

  private SlidingMessagesWindow(int maxMessages) {
    if (maxMessages < 0) {
      throw new IllegalArgumentException(
          "maxMessages must be greater than zero (was %d)".formatted(maxMessages));
    }
    this.maxMessages = maxMessages;
  }

  public static SlidingMessagesWindow of(MemoryConfiguration memory) {
    return new SlidingMessagesWindow(
        Optional.ofNullable(memory)
            .map(MemoryConfiguration::contextWindowSize)
            .orElse(DEFAULT_MAX_MESSAGES));
  }

  public static SlidingMessagesWindow ofSize(int maxMessages) {
    return new SlidingMessagesWindow(maxMessages);
  }

  public int maxMessages() {
    return maxMessages;
  }

  // original implementation see Langchain4j
  public List<Message> apply(List<Message> messages) {
    final var filtered = new ArrayList<>(messages);
    int effectiveCount = (int) filtered.stream().filter(m -> !isToolCallDocumentMessage(m)).count();

    while (effectiveCount > maxMessages) {
      int messageToEvictIndex = 0;

      // don't remove the system message
      if (filtered.getFirst() instanceof SystemMessage) {
        messageToEvictIndex = 1;
      }

      // remove the message at the current index
      Message evictedMessage = filtered.remove(messageToEvictIndex);
      if (!isToolCallDocumentMessage(evictedMessage)) {
        effectiveCount--;
      }

      // remove follow-up tool call results if existing as some LLM providers return an error when
      // receiving tool call results without the original tool call request
      if (evictedMessage instanceof AssistantMessage assistantMessage
          && assistantMessage.hasToolCalls()) {
        while (filtered.size() > messageToEvictIndex
            && filtered.get(messageToEvictIndex) instanceof ToolCallResultMessage) {
          filtered.remove(messageToEvictIndex);
          effectiveCount--;
        }
      }

      // remove follow-up document user messages attached to evicted tool call results
      while (filtered.size() > messageToEvictIndex
          && isToolCallDocumentMessage(filtered.get(messageToEvictIndex))) {
        filtered.remove(messageToEvictIndex);
        // document messages are not counted, no need to decrement
      }
    }

    return List.copyOf(filtered);
  }

  private static boolean isToolCallDocumentMessage(Message message) {
    return message instanceof UserMessage userMessage
        && userMessage.metadata() != null
        && Boolean.TRUE.equals(
            userMessage.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS));
  }
}
