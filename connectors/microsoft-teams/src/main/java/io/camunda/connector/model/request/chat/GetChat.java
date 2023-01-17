/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static io.camunda.connector.RemoveNullFieldsUtil.removeNullFieldsInObject;

import com.microsoft.graph.requests.ChatRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class GetChat extends MSTeamsRequestData {
  @NotBlank @Secret private String chatId;
  private String expand;

  private static final List<String> availableExpandList = List.of("members", "lastMessagePreview");

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatRequest chatRequest = graphClient.chats(chatId).buildRequest();
    if (expand != null && availableExpandList.contains(expand)) {
      chatRequest.expand(expand);
    }
    return removeNullFieldsInObject(chatRequest.get());
  }

  public String getChatId() {
    return chatId;
  }

  public void setChatId(final String chatId) {
    this.chatId = chatId;
  }

  public String getExpand() {
    return expand;
  }

  public void setExpand(final String expand) {
    this.expand = expand;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetChat getChat = (GetChat) o;
    return Objects.equals(chatId, getChat.chatId) && Objects.equals(expand, getChat.expand);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatId, expand);
  }

  @Override
  public String toString() {
    return "GetChat{" + "chatId='" + chatId + "'" + ", expand='" + expand + "'" + "}";
  }
}
