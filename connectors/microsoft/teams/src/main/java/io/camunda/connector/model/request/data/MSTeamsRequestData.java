/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.model.MSTeamsMethodTypes;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  // channel
  @JsonSubTypes.Type(value = CreateChannel.class, name = MSTeamsMethodTypes.CREATE_CHANNEL),
  @JsonSubTypes.Type(value = GetChannel.class, name = MSTeamsMethodTypes.GET_CHANNEL),
  @JsonSubTypes.Type(value = ListChannels.class, name = MSTeamsMethodTypes.LIST_CHANNELS),
  @JsonSubTypes.Type(
      value = GetChannelMessage.class,
      name = MSTeamsMethodTypes.GET_CHANNEL_MESSAGE),
  @JsonSubTypes.Type(
      value = SendMessageToChannel.class,
      name = MSTeamsMethodTypes.SEND_MESSAGE_TO_CHANNEL),
  @JsonSubTypes.Type(
      value = ListChannelMessages.class,
      name = MSTeamsMethodTypes.LIST_CHANNEL_MESSAGES),
  @JsonSubTypes.Type(
      value = ListMessageRepliesInChannel.class,
      name = MSTeamsMethodTypes.LIST_MESSAGE_REPLIES_IN_CHANNEL),
  @JsonSubTypes.Type(
      value = ListChannelMembers.class,
      name = MSTeamsMethodTypes.LIST_CHANNEL_MEMBERS),
  // chat
  @JsonSubTypes.Type(value = CreateChat.class, name = MSTeamsMethodTypes.CREATE_CHAT),
  @JsonSubTypes.Type(value = GetChat.class, name = MSTeamsMethodTypes.GET_CHAT),
  @JsonSubTypes.Type(value = ListChats.class, name = MSTeamsMethodTypes.LIST_CHATS),
  @JsonSubTypes.Type(value = GetMessageInChat.class, name = MSTeamsMethodTypes.GET_MESSAGE_IN_CHAT),
  @JsonSubTypes.Type(value = ListChatMembers.class, name = MSTeamsMethodTypes.LIST_CHAT_MEMBERS),
  @JsonSubTypes.Type(
      value = ListMessagesInChat.class,
      name = MSTeamsMethodTypes.LIST_MESSAGES_IN_CHAT),
  @JsonSubTypes.Type(
      value = SendMessageInChat.class,
      name = MSTeamsMethodTypes.SEND_MESSAGE_IN_CHAT)
})
@TemplateDiscriminatorProperty(
    label = "Conversation type",
    group = "operation",
    name = "type",
    defaultValue = "chat",
    description = "Choose conversation type")
public sealed interface MSTeamsRequestData permits ChatData, ChannelData {}
