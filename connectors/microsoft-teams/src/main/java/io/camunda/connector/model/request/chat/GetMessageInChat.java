/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class GetMessageInChat extends MSTeamsRequestData {
  @NotBlank @Secret private String chatId;
  @NotBlank @Secret private String messageId;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return graphClient.chats(chatId).messages(messageId).buildRequest().get();
  }

  public String getChatId() {
    return chatId;
  }

  public void setChatId(final String chatId) {
    this.chatId = chatId;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(final String messageId) {
    this.messageId = messageId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetMessageInChat that = (GetMessageInChat) o;
    return Objects.equals(chatId, that.chatId) && Objects.equals(messageId, that.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatId, messageId);
  }

  @Override
  public String toString() {
    return "GetMessageInChat{"
        + "chatId='"
        + chatId
        + "'"
        + ", messageId='"
        + messageId
        + "'"
        + "}";
  }
}
