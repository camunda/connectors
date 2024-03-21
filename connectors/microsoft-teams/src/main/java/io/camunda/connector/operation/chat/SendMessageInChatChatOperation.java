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
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.SendMessageInChat;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;

public record SendMessageInChatChatOperation(SendMessageInChat model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    ChatMessage chatMessage = new ChatMessage();
    ItemBody body = new ItemBody();
    body.setContentType(
        Optional.ofNullable(model.bodyType())
            .map(type -> BodyType.forValue(type.toLowerCase(Locale.ROOT)))
            .orElse(BodyType.Text));
    body.setContent(StringEscapeUtils.unescapeJson(model.content()));
    chatMessage.setBody(body);
    return graphClient.chats().byChatId(model.chatId()).messages().post(chatMessage);
  }
}
