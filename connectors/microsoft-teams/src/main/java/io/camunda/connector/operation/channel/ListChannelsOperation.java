/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.request.channel.ListChannels;
import okhttp3.Request;

public record ListChannelsOperation(ListChannels model) implements ChannelOperation {
  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    final var request = graphClient.teams(model.groupId()).allChannels().buildRequest();
    if (model.filter() != null) {
      request.filter(model.filter());
    }
    return request.get();
  }
}
