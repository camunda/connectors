/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import java.util.Objects;

public class ConversationsCreateSlackResponse implements SlackResponse {

  private final Conversation channel;

  public ConversationsCreateSlackResponse(ConversationsCreateResponse response) {
    this.channel = new Conversation(response.getChannel());
  }

  public Conversation getChannel() {
    return channel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConversationsCreateSlackResponse that = (ConversationsCreateSlackResponse) o;
    return Objects.equals(channel, that.channel);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel);
  }

  @Override
  public String toString() {
    return "ConversationsCreateSlackResponse{" + "channel=" + channel + '}';
  }
}
