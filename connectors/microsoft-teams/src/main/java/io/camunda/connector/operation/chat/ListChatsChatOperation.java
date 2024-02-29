/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import static io.camunda.connector.RemoveNullFieldsUtil.removeNullFieldsInObject;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.chat.ListChats;
import okhttp3.Request;

public record ListChatsChatOperation(ListChats model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return removeNullFieldsInObject(graphClient.chats().buildRequest().get());
  }
}
