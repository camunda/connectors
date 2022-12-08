/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import okhttp3.Request;

public class ListChannelMessages extends MSTeamsRequestData {
  private static final String EXPAND_VALUE = "replies";

  @NotBlank @Secret private String groupId;
  @NotBlank @Secret private String channelId;
  private String isExpand;

  @Pattern(regexp = "^([0-9])*$")
  private String top;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessageCollectionRequest request =
        graphClient.teams(groupId).channels(channelId).messages().buildRequest();
    if (Boolean.parseBoolean(isExpand)) {
      request.expand(EXPAND_VALUE);
    }
    if (top != null) {
      request.top(Integer.parseInt(top));
    }
    return request.get();
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

  public String getIsExpand() {
    return isExpand;
  }

  public void setIsExpand(final String isExpand) {
    this.isExpand = isExpand;
  }

  public String getTop() {
    return top;
  }

  public void setTop(final String top) {
    this.top = top;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ListChannelMessages that = (ListChannelMessages) o;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(channelId, that.channelId)
        && Objects.equals(isExpand, that.isExpand)
        && Objects.equals(top, that.top);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, channelId, isExpand, top);
  }

  @Override
  public String toString() {
    return "GetMessageFromChannel{"
        + "groupId='"
        + groupId
        + "'"
        + ", channelId='"
        + channelId
        + "'"
        + ", isExpand='"
        + isExpand
        + "'"
        + ", top='"
        + top
        + "'"
        + "}";
  }
}
