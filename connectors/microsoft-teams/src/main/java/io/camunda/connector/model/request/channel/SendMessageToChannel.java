/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.request.MSTeamsRequestData;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import java.util.Optional;
import okhttp3.Request;
import org.apache.commons.text.StringEscapeUtils;

public class SendMessageToChannel extends MSTeamsRequestData {

  @NotBlank @Secret private String groupId;
  @NotBlank @Secret private String channelId;
  @NotBlank @Secret private String content;
  private String bodyType;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessage chatMessage = new ChatMessage();
    ItemBody body = new ItemBody();
    body.contentType =
        Optional.ofNullable(bodyType)
            .map(type -> BodyType.valueOf(type.toUpperCase()))
            .orElse(BodyType.TEXT);
    body.content = StringEscapeUtils.unescapeJson(content);
    chatMessage.body = body;

    return graphClient
        .teams(groupId)
        .channels(channelId)
        .messages()
        .buildRequest()
        .post(chatMessage);
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

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public String getBodyType() {
    return bodyType;
  }

  public void setBodyType(final String bodyType) {
    this.bodyType = bodyType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendMessageToChannel that = (SendMessageToChannel) o;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(channelId, that.channelId)
        && Objects.equals(content, that.content)
        && Objects.equals(bodyType, that.bodyType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, channelId, content, bodyType);
  }

  @Override
  public String toString() {
    return "SendMessageToChannel{"
        + "groupId='"
        + groupId
        + "'"
        + ", channelId='"
        + channelId
        + "'"
        + ", bodyType='"
        + bodyType
        + "'"
        + "}";
  }
}
