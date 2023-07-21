/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsCreateRequest;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.slack.outbound.SlackRequestData;
import io.camunda.connector.slack.outbound.SlackResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Objects;

public class ConversationsCreateData implements SlackRequestData {

  @NotBlank @Secret private String newChannelName;
  @NotNull private Visibility visibility;

  public enum Visibility {
    PUBLIC,
    PRIVATE
  }

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    ConversationsCreateRequest request =
        ConversationsCreateRequest.builder()
            .name(newChannelName)
            .isPrivate(Visibility.PRIVATE == visibility)
            .build();

    ConversationsCreateResponse response = methodsClient.conversationsCreate(request);

    if (response.isOk()) {
      return new ConversationsCreateSlackResponse(response);
    } else {
      throw new RuntimeException(response.getError());
    }
  }

  public String getNewChannelName() {
    return newChannelName;
  }

  public void setNewChannelName(String newChannelName) {
    this.newChannelName = newChannelName;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public void setVisibility(Visibility visibility) {
    this.visibility = visibility;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConversationsCreateData that = (ConversationsCreateData) o;
    return Objects.equals(newChannelName, that.newChannelName) && visibility == that.visibility;
  }

  @Override
  public int hashCode() {
    return Objects.hash(newChannelName, visibility);
  }

  @Override
  public String toString() {
    return "ConversationsCreateData{"
        + "newChannelName='"
        + newChannelName
        + '\''
        + ", visibility="
        + visibility
        + '}';
  }
}
