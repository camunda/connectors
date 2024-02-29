/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.OrderBy;
import io.camunda.connector.model.request.chat.ListMessagesInChat;
import okhttp3.Request;

public record ListMessagesInChatChatOperation(ListMessagesInChat model) implements ChatOperation {

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessageCollectionRequest request =
        graphClient.chats(model.chatId()).messages().buildRequest();
    if (model.orderBy() != OrderBy.withoutOrdering) {
      request.orderBy(model.orderBy().getValue());
    }
    if (model.filter() != null) {
      request.filter(model.filter());
    }
    if (model.top() != null) {
      request.top(Integer.parseInt(model.top()));
    }
    return request.get();
  }
}
