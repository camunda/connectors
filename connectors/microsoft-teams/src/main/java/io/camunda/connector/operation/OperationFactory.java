/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation;

import io.camunda.connector.model.request.data.CreateChannel;
import io.camunda.connector.model.request.data.CreateChat;
import io.camunda.connector.model.request.data.GetChannel;
import io.camunda.connector.model.request.data.GetChannelMessage;
import io.camunda.connector.model.request.data.GetChat;
import io.camunda.connector.model.request.data.GetMessageInChat;
import io.camunda.connector.model.request.data.ListChannelMembers;
import io.camunda.connector.model.request.data.ListChannelMessages;
import io.camunda.connector.model.request.data.ListChannels;
import io.camunda.connector.model.request.data.ListChatMembers;
import io.camunda.connector.model.request.data.ListChats;
import io.camunda.connector.model.request.data.ListMessageRepliesInChannel;
import io.camunda.connector.model.request.data.ListMessagesInChat;
import io.camunda.connector.model.request.data.MSTeamsRequestData;
import io.camunda.connector.model.request.data.SendMessageInChat;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import io.camunda.connector.operation.channel.CreateChannelOperation;
import io.camunda.connector.operation.channel.GetChannelMessageOperation;
import io.camunda.connector.operation.channel.GetChannelOperation;
import io.camunda.connector.operation.channel.ListChannelMembersOperation;
import io.camunda.connector.operation.channel.ListChannelMessagesOperation;
import io.camunda.connector.operation.channel.ListChannelsOperation;
import io.camunda.connector.operation.channel.ListMessageRepliesInChannelOperation;
import io.camunda.connector.operation.channel.SendMessageToChannelOperation;
import io.camunda.connector.operation.chat.CreateChatOperation;
import io.camunda.connector.operation.chat.GetChatOperation;
import io.camunda.connector.operation.chat.GetMessageInChatOperation;
import io.camunda.connector.operation.chat.ListChatMembersChatOperation;
import io.camunda.connector.operation.chat.ListChatsOperation;
import io.camunda.connector.operation.chat.ListMessagesInChatOperation;
import io.camunda.connector.operation.chat.SendMessageInChatChatOperation;

public class OperationFactory {
  public Operation getService(final MSTeamsRequestData data) {
    return switch (data) {
        // chat
      case CreateChat model -> new CreateChatOperation(model);
      case GetChat model -> new GetChatOperation(model);
      case GetMessageInChat model -> new GetMessageInChatOperation(model);
      case ListChatMembers model -> new ListChatMembersChatOperation(model);
      case ListChats model -> new ListChatsOperation(model);
      case ListMessagesInChat model -> new ListMessagesInChatOperation(model);
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
    };
  }
}
