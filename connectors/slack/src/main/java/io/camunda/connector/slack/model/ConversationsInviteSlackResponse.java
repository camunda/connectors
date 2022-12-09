/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.model;

import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import io.camunda.connector.slack.SlackResponse;
import io.camunda.connector.slack.dto.Conversation;
import java.util.Objects;

public class ConversationsInviteSlackResponse implements SlackResponse {

  private final Conversation channel;
  private final String needed;
  private final String provided;

  public ConversationsInviteSlackResponse(ConversationsInviteResponse conversationsInviteResponse) {
    this.channel = new Conversation(conversationsInviteResponse.getChannel());
    this.needed = conversationsInviteResponse.getNeeded();
    this.provided = conversationsInviteResponse.getProvided();
  }

  public Conversation getChannel() {
    return channel;
  }

  public String getNeeded() {
    return needed;
  }

  public String getProvided() {
    return provided;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConversationsInviteSlackResponse that = (ConversationsInviteSlackResponse) o;
    return Objects.equals(channel, that.channel)
        && Objects.equals(needed, that.needed)
        && Objects.equals(provided, that.provided);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel, needed, provided);
  }
}
