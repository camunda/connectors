/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration;
import java.util.List;

public interface ChatMessageConverter {

  default List<ChatMessage> map(Message message, ProviderConfiguration providerConfiguration) {
    return switch (message) {
      case SystemMessage systemMessage -> List.of(fromSystemMessage(systemMessage));
      case UserMessage userMessage -> List.of(fromUserMessage(userMessage));
      case AssistantMessage assistantMessage ->
          List.of(fromAssistantMessage(assistantMessage, providerConfiguration));
      case ToolCallResultMessage toolCallResultMessage ->
          fromToolCallResultMessage(toolCallResultMessage).stream()
              .map(ChatMessage.class::cast)
              .toList();
      default -> throw new IllegalArgumentException("Unknown message type: " + message.getClass());
    };
  }

  default List<ChatMessage> map(
      List<Message> messages, ProviderConfiguration providerConfiguration) {
    return messages.stream()
        .map(message -> map(message, providerConfiguration))
        .flatMap(List::stream)
        .toList();
  }

  dev.langchain4j.data.message.SystemMessage fromSystemMessage(SystemMessage systemMessage);

  dev.langchain4j.data.message.UserMessage fromUserMessage(UserMessage userMessage);

  dev.langchain4j.data.message.AiMessage fromAssistantMessage(
      AssistantMessage assistantMessage, ProviderConfiguration providerConfiguration);

  AssistantMessage toAssistantMessage(
      ChatResponse chatResponse, ProviderConfiguration providerConfiguration);

  List<dev.langchain4j.data.message.ToolExecutionResultMessage> fromToolCallResultMessage(
      ToolCallResultMessage toolCallResultMessage);
}
