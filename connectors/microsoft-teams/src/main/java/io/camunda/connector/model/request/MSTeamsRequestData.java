/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.microsoft.graph.requests.GraphServiceClient;
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
import okhttp3.Request;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "method")
@JsonSubTypes({
  // channel
  @JsonSubTypes.Type(value = CreateChannel.class, name = "createChannel"),
  @JsonSubTypes.Type(value = GetChannel.class, name = "getChannel"),
  @JsonSubTypes.Type(value = ListChannels.class, name = "listAllChannels"),
  @JsonSubTypes.Type(value = GetChannelMessage.class, name = "getMessageFromChannel"),
  @JsonSubTypes.Type(value = SendMessageToChannel.class, name = "sendMessageToChannel"),
  @JsonSubTypes.Type(value = ListChannelMessages.class, name = "listChannelMessages"),
  @JsonSubTypes.Type(
      value = ListMessageRepliesInChannel.class,
      name = "listMessageRepliesInChannel"),
  @JsonSubTypes.Type(value = ListChannelMembers.class, name = "listMembersInChannel"),
  // chat
  @JsonSubTypes.Type(value = CreateChat.class, name = "createChat"),
  @JsonSubTypes.Type(value = GetChat.class, name = "getChat"),
  @JsonSubTypes.Type(value = ListChats.class, name = "listChats"),
  @JsonSubTypes.Type(value = GetMessageInChat.class, name = "getMessageFromChat"),
  @JsonSubTypes.Type(value = ListChatMembers.class, name = "listMembersOfChat"),
  @JsonSubTypes.Type(value = ListMessagesInChat.class, name = "listMessagesInChat"),
  @JsonSubTypes.Type(value = SendMessageInChat.class, name = "sendMessageToChat")
})
public abstract class MSTeamsRequestData {

  private transient String method;

  public abstract Object invoke(final GraphServiceClient<Request> graphClient);
}
