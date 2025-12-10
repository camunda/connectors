/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.chat;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.RemoveNullFieldsUtil;
import io.camunda.connector.model.request.data.GetChat;
import java.util.List;

public record GetChatOperation(GetChat model) implements ChatOperation {
  private static final List<String> availableExpandList = List.of("members", "lastMessagePreview");

  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    return RemoveNullFieldsUtil.removeNullFieldsInObject(
        graphClient
            .chats()
            .byChatId(model.chatId())
            .get(
                getRequestConfiguration -> {
                  if (model.expand() != null
                      && availableExpandList.contains(model.expand())
                      && getRequestConfiguration.queryParameters != null) {
                    getRequestConfiguration.queryParameters.expand = new String[] {model.expand()};
                  }
                }));
  }
}
