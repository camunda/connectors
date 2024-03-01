/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.data.ListChatMembers;
import okhttp3.Request;

public record ListChatMembersChatOperation(ListChatMembers model) implements ChatOperation {
  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return graphClient.chats(model.chatId()).members().buildRequest().get();
  }
}
