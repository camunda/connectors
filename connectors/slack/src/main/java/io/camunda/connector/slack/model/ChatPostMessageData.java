/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.User;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.slack.SlackRequestData;
import io.camunda.connector.slack.SlackResponse;
import io.camunda.connector.slack.utils.DataLookupService;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotBlank;
import org.apache.commons.text.StringEscapeUtils;

public class ChatPostMessageData implements SlackRequestData {

  @NotBlank @Secret private String channel;
  @NotBlank @Secret private String text;

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    if (channel.startsWith("@")) {
      channel = DataLookupService.getUserIdByName(channel.substring(1), methodsClient);
    } else if (DataLookupService.isEmail(channel)) {
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
