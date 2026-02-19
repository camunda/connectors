/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.List;

public interface ChatMessageConverter {

  default List<ChatMessage> map(Message message) {
    return switch (message) {
      case SystemMessage systemMessage -> List.of(fromSystemMessage(systemMessage));
      case UserMessage userMessage -> List.of(fromUserMessage(userMessage));
      case AssistantMessage assistantMessage -> List.of(fromAssistantMessage(assistantMessage));
      case ToolCallResultMessage toolCallResultMessage ->
          fromToolCallResultMessage(toolCallResultMessage).stream()
              .map(ChatMessage.class::cast)
              .toList();
      default -> throw new IllegalArgumentException("Unknown message type: " + message.getClass());
    };
  }

  default List<ChatMessage> map(List<Message> messages) {
    return messages.stream().map(this::map).flatMap(List::stream).toList();
  }

  dev.langchain4j.data.message.SystemMessage fromSystemMessage(SystemMessage systemMessage);

  dev.langchain4j.data.message.UserMessage fromUserMessage(UserMessage userMessage);

  dev.langchain4j.data.message.AiMessage fromAssistantMessage(AssistantMessage assistantMessage);

  AssistantMessage toAssistantMessage(ChatResponse chatResponse);

  List<dev.langchain4j.data.message.ToolExecutionResultMessage> fromToolCallResultMessage(
      ToolCallResultMessage toolCallResultMessage);
}
