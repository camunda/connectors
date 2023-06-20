/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.slack.outbound.SlackRequestData;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ConversationsInviteData implements SlackRequestData {

  @NotBlank @Secret private String channelName;
  @NotNull @Secret private Object users;

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    Collection<?> userInput = null;
    if (users instanceof Collection<?>) {
      userInput = (Collection<?>) users;
    } else if (users instanceof String) {
      userInput = DataLookupService.convertStringToList((String) users);
    } else {
      // We accept only List or String input for users
      throw new IllegalArgumentException(
          "Invalid input type for users. Supported types are: List<String> and String");
    }

    List<String> userList = DataLookupService.getUserIdsFromUsers(userInput, methodsClient);
    ConversationsInviteRequest request =
        ConversationsInviteRequest.builder()
            .channel(DataLookupService.getChannelIdByName(channelName, methodsClient))
            .users(userList)
            .build();

    ConversationsInviteResponse response = methodsClient.conversationsInvite(request);

    if (response.isOk()) {
      return new ConversationsInviteSlackResponse(response);
    } else {
      throw new RuntimeException(response.getError());
    }
  }

  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
  }

  public Object getUsers() {
    return users;
  }

  public void setUsers(Object users) {
    this.users = users;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConversationsInviteData that = (ConversationsInviteData) o;
    return channelName.equals(that.channelName) && Objects.equals(users, that.users);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelName, users);
  }

  @Override
  public String toString() {
    return "ConversationsInviteData{"
        + "channelName='"
        + channelName
        + '\''
        + ", users="
        + users
        + '}';
  }
}
