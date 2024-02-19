/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import io.camunda.connector.slack.outbound.SlackResponse;

public record ChatPostMessageSlackResponse(String ts, String channel, Message message)
    implements SlackResponse {
  public ChatPostMessageSlackResponse(ChatPostMessageResponse chatPostMessageResponse) {
    this(
        chatPostMessageResponse.getTs(),
        chatPostMessageResponse.getChannel(),
        new Message(chatPostMessageResponse.getMessage()));
  }

  protected record Message(
      String type, String team, String user, String text, String ts, String appId, String botId) {
    public Message(com.slack.api.model.Message message) {
      this(
          message.getType(),
          message.getTeam(),
          message.getUser(),
          message.getText(),
          message.getTs(),
          message.getAppId(),
          message.getBotId());
    }
  }
}
