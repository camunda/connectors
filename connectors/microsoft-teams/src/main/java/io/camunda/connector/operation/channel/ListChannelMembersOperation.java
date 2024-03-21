/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.data.ListChannelMembers;
import okhttp3.Request;

public record ListChannelMembersOperation(ListChannelMembers model) implements ChannelOperation {
  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return graphClient
        .teams(model.groupId())
        .channels(model.channelId())
        .members()
        .buildRequest()
        .get();
  }
}
