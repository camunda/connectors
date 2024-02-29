/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import static io.camunda.connector.RemoveNullFieldsUtil.removeNullFieldsInObject;

import com.microsoft.graph.requests.ChatRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.chat.GetChat;
import java.util.List;
import okhttp3.Request;

public record GetChatChatOperation(GetChat model) implements ChatOperation {
  private static final List<String> availableExpandList = List.of("members", "lastMessagePreview");

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatRequest chatRequest = graphClient.chats(model.chatId()).buildRequest();
    if (model.expand() != null && availableExpandList.contains(model.expand())) {
      chatRequest.expand(model.expand());
    }
    return removeNullFieldsInObject(chatRequest.get());
  }
}
