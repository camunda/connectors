/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation;

import io.camunda.connector.model.request.MSTeamsRequestData;
import io.camunda.connector.model.request.channel.CreateChannel;
import io.camunda.connector.model.request.channel.GetChannel;
import io.camunda.connector.model.request.channel.GetChannelMessage;
import io.camunda.connector.model.request.channel.ListChannelMembers;
import io.camunda.connector.model.request.channel.ListChannelMessages;
import io.camunda.connector.model.request.channel.ListChannels;
import io.camunda.connector.model.request.channel.ListMessageRepliesInChannel;
import io.camunda.connector.model.request.channel.SendMessageToChannel;
import io.camunda.connector.model.request.chat.CreateChat;
import io.camunda.connector.model.request.chat.GetChat;
import io.camunda.connector.model.request.chat.GetMessageInChat;
import io.camunda.connector.model.request.chat.ListChatMembers;
import io.camunda.connector.model.request.chat.ListChats;
import io.camunda.connector.model.request.chat.ListMessagesInChat;
import io.camunda.connector.model.request.chat.SendMessageInChat;
import io.camunda.connector.operation.channel.CreateChannelOperation;
import io.camunda.connector.operation.channel.GetChannelMessageOperation;
import io.camunda.connector.operation.channel.GetChannelOperation;
import io.camunda.connector.operation.channel.ListChannelMembersOperation;
import io.camunda.connector.operation.channel.ListChannelMessagesOperation;
import io.camunda.connector.operation.channel.ListChannelsOperation;
import io.camunda.connector.operation.channel.ListMessageRepliesInChannelOperation;
import io.camunda.connector.operation.channel.SendMessageToChannelOperation;
import io.camunda.connector.operation.chat.CreateChatChatOperation;
import io.camunda.connector.operation.chat.GetChatChatOperation;
import io.camunda.connector.operation.chat.GetMessageInChatChatOperation;
import io.camunda.connector.operation.chat.ListChatMembersChatOperation;
import io.camunda.connector.operation.chat.ListChatsChatOperation;
import io.camunda.connector.operation.chat.ListMessagesInChatChatOperation;
import io.camunda.connector.operation.chat.SendMessageInChatChatOperation;

public class OperationFactory {
  public Operation getService(final MSTeamsRequestData data) {
    return switch (data) {
        // chat
      case CreateChat model -> new CreateChatChatOperation(model);
      case GetChat model -> new GetChatChatOperation(model);
      case GetMessageInChat model -> new GetMessageInChatChatOperation(model);
      case ListChatMembers model -> new ListChatMembersChatOperation(model);
      case ListChats model -> new ListChatsChatOperation(model);
      case ListMessagesInChat model -> new ListMessagesInChatChatOperation(model);
      case SendMessageInChat model -> new SendMessageInChatChatOperation(model);
        // channel
      case CreateChannel model -> new CreateChannelOperation(model);
      case GetChannel model -> new GetChannelOperation(model);
      case GetChannelMessage model -> new GetChannelMessageOperation(model);
      case ListChannelMembers model -> new ListChannelMembersOperation(model);
      case ListChannelMessages model -> new ListChannelMessagesOperation(model);
      case ListChannels model -> new ListChannelsOperation(model);
      case ListMessageRepliesInChannel model -> new ListMessageRepliesInChannelOperation(model);
      case SendMessageToChannel model -> new SendMessageToChannelOperation(model);
      default -> throw new IllegalStateException("Unexpected value: " + data);
    };
  }
}
