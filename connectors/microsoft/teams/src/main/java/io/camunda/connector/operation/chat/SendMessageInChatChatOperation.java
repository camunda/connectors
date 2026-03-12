/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.Attachment;
import io.camunda.connector.model.request.data.SendMessageInChat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record SendMessageInChatChatOperation(SendMessageInChat model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    ChatMessage chatMessage = new ChatMessage();

    ItemBody body = new ItemBody();
    BodyType resolvedBodyType =
        Optional.ofNullable(model.bodyType())
            .map(type -> BodyType.forValue(type.toLowerCase(Locale.ROOT)))
            .orElse(BodyType.Text);
    String content = model.content();

    if (model.attachments() != null && !model.attachments().isEmpty()) {
      List<ChatMessageAttachment> attachments = new ArrayList<>();
      for (Attachment card : model.attachments()) {
        ChatMessageAttachment attachment = new ChatMessageAttachment();
        attachment.setId(card.id());
        attachment.setContentType(card.contentType());
        attachment.setContent(card.content());
        attachments.add(attachment);
      }
      chatMessage.setAttachments(attachments);

      StringBuilder sb = new StringBuilder(content);
      for (Attachment card : model.attachments()) {
        if (card.id() != null && !content.contains("<attachment id=\"" + card.id() + "\">")) {
          sb.append("<attachment id=\"").append(card.id()).append("\"></attachment>");
        }
      }
      content = sb.toString();
    }

    body.setContentType(resolvedBodyType);
    body.setContent(content);
    chatMessage.setBody(body);

    return graphClient.chats().byChatId(model.chatId()).messages().post(chatMessage);
  }
}
