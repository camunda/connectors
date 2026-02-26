/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.SendMessageInChat;
import io.camunda.connector.operation.CardAttachmentHelper;

public record SendMessageInChatChatOperation(SendMessageInChat model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    ChatMessage chatMessage = new ChatMessage();

    CardAttachmentHelper.configureMessageBody(
        chatMessage, model.content(), model.bodyType(), model.attachmentsJson());

    return graphClient.chats().byChatId(model.chatId()).messages().post(chatMessage);
  }
}
