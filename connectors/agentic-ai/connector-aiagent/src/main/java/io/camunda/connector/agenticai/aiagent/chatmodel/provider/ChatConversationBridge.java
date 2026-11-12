/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatAssistantMessage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatConversation;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequestMessage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatSystemMessage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatUserMessage;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.List;

/**
 * The persisted messages of a {@link ChatConversation}, for the provider converters that still read
 * the persisted message types.
 */
public final class ChatConversationBridge {

  private ChatConversationBridge() {}

  public static ConversationSnapshot toSnapshot(ChatConversation conversation) {
    return new ConversationSnapshot(
        conversation.messages().stream().map(ChatConversationBridge::message).toList(),
        conversation.toolDefinitions());
  }

  private static Message message(ChatRequestMessage message) {
    return switch (message) {
      case ChatSystemMessage system ->
          SystemMessage.builder().content(List.<Content>copyOf(system.content())).build();
      case ChatUserMessage user ->
          UserMessage.builder().content(List.<Content>copyOf(user.content())).build();
      case ChatAssistantMessage assistant ->
          AssistantMessage.builder()
              .content(List.<Content>copyOf(assistant.content()))
              .toolCalls(assistant.toolCalls())
              .messageId(assistant.messageId())
              .metadata(assistant.metadata())
              .build();
      case ChatToolCallResultMessage results ->
          ToolCallResultMessage.builder()
              .results(
                  results.results().stream()
                      .map(result ->
                          ToolCallResultContent.builder()
                              .id(result.id())
                              .name(result.name())
                              .content(List.<Content>copyOf(result.content()))
                              .build())
                      .toList())
              .build();
    };
  }
}
