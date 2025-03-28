/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record SendMessageToChannelOperation(SendMessageToChannel model)
    implements ChannelOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    ChatMessage chatMessage = new ChatMessage();
    ItemBody body = new ItemBody();
    body.setContentType(
        Optional.ofNullable(model.bodyType())
            .map(type -> BodyType.forValue(type.toLowerCase(Locale.ROOT)))
            .orElse(BodyType.Text));
    body.setContent(model.content());
    if (model.documents() != null) {
      DocumentHandler documentHandler = new DocumentHandler(graphClient, model);
      List<ChatMessageAttachment> attachments = documentHandler.handleDocuments();
      chatMessage.setAttachments(attachments);
    }
    chatMessage.setBody(body);
    return graphClient
        .teams()
        .byTeamId(model.groupId())
        .channels()
        .byChannelId(model.channelId())
        .messages()
        .post(chatMessage);
  }
}
