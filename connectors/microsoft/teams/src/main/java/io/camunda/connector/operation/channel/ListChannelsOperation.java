/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.request.data.ListChannels;

public record ListChannelsOperation(ListChannels model) implements ChannelOperation {
  @Override
  public Object invoke(final GraphServiceClient graphClient) {
    return graphClient
        .teams()
        .byTeamId(model.groupId())
        .allChannels()
        .get(
            getRequestConfiguration -> {
              if (model.filter() != null && getRequestConfiguration.queryParameters != null) {
                getRequestConfiguration.queryParameters.filter = model.filter();
              }
            });
  }
}
