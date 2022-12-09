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
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import okhttp3.Request;

public class ListMessageRepliesInChannel extends MSTeamsRequestData {

  @NotBlank @Secret private String groupId;
  @NotBlank @Secret private String channelId;
  @NotBlank @Secret private String messageId;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    return graphClient
        .teams(groupId)
        .channels(channelId)
        .messages(messageId)
        .replies()
        .buildRequest()
        .get();
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

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(final String messageId) {
    this.messageId = messageId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListMessageRepliesInChannel that = (ListMessageRepliesInChannel) o;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(channelId, that.channelId)
        && Objects.equals(messageId, that.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, channelId, messageId);
  }

  @Override
  public String toString() {
    return "ListMessageRepliesInChannel{"
        + "groupId='"
        + groupId
        + "'"
        + ", channelId='"
        + channelId
        + "'"
        + ", messageId='"
        + messageId
        + "'"
        + "}";
  }
}
