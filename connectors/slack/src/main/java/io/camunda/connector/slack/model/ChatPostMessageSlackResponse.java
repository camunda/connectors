/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.model;

import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import io.camunda.connector.slack.SlackResponse;
import java.util.Objects;

public class ChatPostMessageSlackResponse implements SlackResponse {

  private final String ts;
  private final String channel;

  private final Message message;

  public ChatPostMessageSlackResponse(ChatPostMessageResponse chatPostMessageResponse) {
    ts = chatPostMessageResponse.getTs();
    channel = chatPostMessageResponse.getChannel();
    message = new Message(chatPostMessageResponse.getMessage());
  }

  public String getTs() {
    return ts;
  }

  public String getChannel() {
    return channel;
  }

  public Message getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ChatPostMessageSlackResponse that = (ChatPostMessageSlackResponse) o;
    return Objects.equals(ts, that.ts)
        && Objects.equals(channel, that.channel)
        && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ts, channel, message);
  }

  @Override
  public String toString() {
    return "ChatPostMessageSlackResponse{"
        + "ts='"
        + ts
        + '\''
        + ", channel='"
        + channel
        + '\''
        + ", message="
        + message
        + '}';
  }

  protected static class Message {
    private final String type;
    private final String team;
    private final String user;
    private final String text;
    private final String ts;
    private final String appId;
    private final String botId;

    public Message(com.slack.api.model.Message message) {
      type = message.getType();
      team = message.getTeam();
      user = message.getUser();
      text = message.getText();
      ts = message.getTs();
      appId = message.getAppId();
      botId = message.getBotId();
    }

    public String getType() {
      return type;
    }

    public String getTeam() {
      return team;
    }

    public String getUser() {
      return user;
    }

    public String getText() {
      return text;
    }

    public String getTs() {
      return ts;
    }

    public String getAppId() {
      return appId;
    }

    public String getBotId() {
      return botId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Message message = (Message) o;
      return Objects.equals(type, message.type)
          && Objects.equals(team, message.team)
          && Objects.equals(user, message.user)
          && Objects.equals(text, message.text)
          && Objects.equals(ts, message.ts)
          && Objects.equals(appId, message.appId)
          && Objects.equals(botId, message.botId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, team, user, text, ts, appId, botId);
    }

    @Override
    public String toString() {
      return "Message{"
          + "type='"
          + type
          + '\''
          + ", team='"
          + team
          + '\''
          + ", user='"
          + user
          + '\''
          + ", text='"
          + text
          + '\''
          + ", ts='"
          + ts
          + '\''
          + ", appId='"
          + appId
          + '\''
          + ", botId='"
          + botId
          + '\''
          + '}';
    }
  }
}
