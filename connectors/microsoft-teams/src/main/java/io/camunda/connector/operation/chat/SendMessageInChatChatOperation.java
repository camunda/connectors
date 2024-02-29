/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.chat.SendMessageInChat;
import java.util.Optional;
import okhttp3.Request;
import org.apache.commons.text.StringEscapeUtils;

public record SendMessageInChatChatOperation(SendMessageInChat model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessage chatMessage = new ChatMessage();
    ItemBody body = new ItemBody();
    body.contentType =
        Optional.ofNullable(model.bodyType())
            .map(type -> BodyType.valueOf(type.toUpperCase()))
            .orElse(BodyType.TEXT);
    body.content = StringEscapeUtils.unescapeJson(model.content());
    chatMessage.body = body;
    return graphClient.chats(model.chatId()).messages().buildRequest().post(chatMessage);
  }
}
