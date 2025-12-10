/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.OrderBy;
import io.camunda.connector.model.request.data.ListMessagesInChat;

public record ListMessagesInChatOperation(ListMessagesInChat model) implements ChatOperation {

  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    return graphClient
        .chats()
        .byChatId(model.chatId())
        .messages()
        .get(
            getRequestConfiguration -> {
              if (getRequestConfiguration.queryParameters != null) {
                var params = getRequestConfiguration.queryParameters;
                if (model.orderBy() != OrderBy.withoutOrdering) {
                  params.orderby = new String[] {model.orderBy().getValue()};
                }
                if (model.filter() != null) {
                  params.filter = model.filter();
                }
                if (model.top() != null) {
                  params.top = Integer.parseInt(model.top());
                }
              }
            });
  }
}
