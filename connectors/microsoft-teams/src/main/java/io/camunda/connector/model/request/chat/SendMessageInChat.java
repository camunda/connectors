/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class SendMessageInChat extends MSTeamsRequestData {
  @NotBlank @Secret private String chatId;

  @NotBlank @Secret private String content;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessage chatMessage = new ChatMessage();
    ItemBody body = new ItemBody();
    body.content = content;
    chatMessage.body = body;
    return graphClient.chats(chatId).messages().buildRequest().post(chatMessage);
  }

  public String getChatId() {
    return chatId;
  }

  public void setChatId(final String chatId) {
    this.chatId = chatId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendMessageInChat that = (SendMessageInChat) o;
    return Objects.equals(chatId, that.chatId) && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatId, content);
  }

  @Override
  public String toString() {
    return "SendMessageInChat{" + "chatId='" + chatId + "'" + ", content='" + content + "'" + "}";
  }
}
