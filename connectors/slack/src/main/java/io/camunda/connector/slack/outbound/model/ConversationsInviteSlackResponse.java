/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import io.camunda.connector.slack.outbound.SlackResponse;
import io.camunda.connector.slack.outbound.dto.Conversation;

public record ConversationsInviteSlackResponse(Conversation channel, String needed, String provided)
    implements SlackResponse {

  public ConversationsInviteSlackResponse(ConversationsInviteResponse conversationsInviteResponse) {
    this(
        new Conversation(conversationsInviteResponse.getChannel()),
        conversationsInviteResponse.getNeeded(),
        conversationsInviteResponse.getProvided());
  }
}
