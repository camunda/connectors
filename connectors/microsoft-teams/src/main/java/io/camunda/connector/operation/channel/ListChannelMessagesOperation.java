/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import static io.camunda.connector.model.request.data.ListChannelMessages.EXPAND_VALUE;

import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.data.ListChannelMessages;
import okhttp3.Request;

public record ListChannelMessagesOperation(ListChannelMessages model) implements ChannelOperation {
  @Override
  public ChatMessageCollectionPage invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessageCollectionRequest request =
        graphClient.teams(model.groupId()).channels(model.channelId()).messages().buildRequest();
    if (Boolean.parseBoolean(model.isExpand())) {
      request.expand(EXPAND_VALUE);
    }
    if (model.top() != null) {
      request.top(Integer.parseInt(model.top()));
    }
    return request.get();
  }
}
