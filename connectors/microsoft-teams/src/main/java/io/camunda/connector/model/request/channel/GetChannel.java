/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import okhttp3.Request;

public class GetChannel extends MSTeamsRequestData {

  @NotBlank @Secret private String groupId;
  @NotBlank @Secret private String channelId;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return graphClient.teams(groupId).channels(channelId).buildRequest().get();
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(final String channelId) {
    this.channelId = channelId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetChannel that = (GetChannel) o;
    return Objects.equals(groupId, that.groupId) && Objects.equals(channelId, that.channelId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, channelId);
  }

  @Override
  public String toString() {
    return "GetChannel{" + "groupId='" + groupId + "'" + ", channelId='" + channelId + "'" + "}";
  }
}
