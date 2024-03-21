/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import static io.camunda.connector.model.request.data.ListChannelMessages.EXPAND_VALUE;

import com.microsoft.graph.models.ChatMessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.ListChannelMessages;

public record ListChannelMessagesOperation(ListChannelMessages model) implements ChannelOperation {
  @Override
  public ChatMessageCollectionResponse invoke(final GraphServiceClient graphClient) {
    return graphClient
        .teams()
        .byTeamId(model.groupId())
        .channels()
        .byChannelId(model.channelId())
        .messages()
        .get(
            requestConfiguration -> {
              if (requestConfiguration.queryParameters != null) {
                if (model.top() != null) {
                  requestConfiguration.queryParameters.top = Integer.parseInt(model.top());
                }
                if (Boolean.parseBoolean(model.isExpand())) {
                  requestConfiguration.queryParameters.expand = new String[] {EXPAND_VALUE};
                }
              }
            });
  }
}
