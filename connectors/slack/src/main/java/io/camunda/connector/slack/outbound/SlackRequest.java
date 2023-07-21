/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import io.camunda.connector.slack.outbound.model.ConversationsCreateData;
import io.camunda.connector.slack.outbound.model.ConversationsInviteData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Objects;

public class SlackRequest<T extends SlackRequestData> {

  @NotBlank private String token;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "method")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = ChatPostMessageData.class, name = "chat.postMessage"),
        @JsonSubTypes.Type(value = ConversationsCreateData.class, name = "conversations.create"),
        @JsonSubTypes.Type(value = ConversationsInviteData.class, name = "conversations.invite")
      })
  @Valid
  @NotNull
  private T data;

  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    MethodsClient methods = slack.methods(token);
    return data.invoke(methods);
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SlackRequest<?> that = (SlackRequest<?>) o;
    return Objects.equals(token, that.token) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, data);
  }

  @Override
  public String toString() {
    return "SlackRequest{" + "token=[redacted]" + ", data=" + data + '}';
  }
}
