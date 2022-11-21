/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.User;
import io.camunda.connector.api.annotation.Secret;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotBlank;
import org.apache.commons.text.StringEscapeUtils;

public class ChatPostMessageData implements SlackRequestData {

  private static final String EMAIL_REGEX = "^.+[@].+[.].{2,4}$";

  @NotBlank @Secret private String channel;
  @NotBlank @Secret private String text;

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    if (channel.startsWith("@")) {
      channel = getUserId(channel.substring(1), methodsClient);
    } else if (isEmail(channel)) {
      channel = getUserIdByEmail(methodsClient);
    }
    ChatPostMessageRequest request =
        ChatPostMessageRequest.builder()
            .channel(channel)
            // Temporary workaround related to camunda/zeebe#9859
            .text(StringEscapeUtils.unescapeJson(text))
            .linkNames(true) // Enables message formatting
            .build();

    ChatPostMessageResponse chatPostMessageResponse = methodsClient.chatPostMessage(request);
    if (chatPostMessageResponse.isOk()) {
      return new ChatPostMessageSlackResponse(chatPostMessageResponse);
    } else {
      throw new RuntimeException(chatPostMessageResponse.getError());
    }
  }

  private String getUserId(String userName, MethodsClient methodsClient) {
    String userId = null;
    String nextCursor = null;

    do {
      UsersListRequest request = UsersListRequest.builder().limit(100).cursor(nextCursor).build();

      try {
        UsersListResponse response = methodsClient.usersList(request);
        if (response.isOk()) {
          userId =
              response.getMembers().stream()
                  .filter(user -> userName.equals(user.getRealName()))
                  .map(User::getId)
                  .findFirst()
                  .orElse(null);
          nextCursor = response.getResponseMetadata().getNextCursor();
        } else {
          throw new RuntimeException(
              "Unable to find user with name: " + userName + "; message: " + response.getError());
        }
      } catch (Exception e) {
        throw new RuntimeException("Unable to find user with name: " + userName, e);
      }

    } while (userId == null && nextCursor != null && !nextCursor.isBlank());

    if (userId == null) {
      throw new RuntimeException("Unable to find user with name: " + userName);
    }

    return userId;
  }

  private boolean isEmail(final String str) {
    return str.matches(EMAIL_REGEX);
  }

  private String getUserIdByEmail(final MethodsClient methodsClient)
      throws IOException, SlackApiException {
    UsersLookupByEmailRequest lookupByEmailRequest =
        UsersLookupByEmailRequest.builder().email(channel).build();

    return Optional.ofNullable(methodsClient.usersLookupByEmail(lookupByEmailRequest))
        .map(UsersLookupByEmailResponse::getUser)
        .map(User::getId)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "User with email "
                        + channel
                        + " not found; or unable 'users:read.email' permission"));
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatPostMessageData that = (ChatPostMessageData) o;
    return Objects.equals(channel, that.channel) && Objects.equals(text, that.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel, text);
  }

  @Override
  public String toString() {
    return "ChatPostMessageData{" + "channel='" + channel + '\'' + ", text='" + text + '\'' + '}';
  }
}
