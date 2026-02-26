/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import io.camunda.connector.operation.CardAttachmentHelper;
import java.util.List;

public record SendMessageToChannelOperation(SendMessageToChannel model)
    implements ChannelOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    ChatMessage chatMessage = new ChatMessage();

    CardAttachmentHelper.configureMessageBody(
        chatMessage, model.content(), model.bodyType(), model.attachmentsJson());

    if (model.documents() != null) {
      DocumentHandler documentHandler = new DocumentHandler(graphClient, model);
      List<ChatMessageAttachment> documentAttachments = documentHandler.handleDocuments();
      List<ChatMessageAttachment> existing = chatMessage.getAttachments();
      if (existing != null) {
        existing.addAll(documentAttachments);
      } else {
        chatMessage.setAttachments(documentAttachments);
      }
    }

    return graphClient
        .teams()
        .byTeamId(model.groupId())
        .channels()
        .byChannelId(model.channelId())
        .messages()
        .post(chatMessage);
  }
}
