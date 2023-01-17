/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import io.camunda.connector.model.authentication.BearerAuthentication;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.model.authentication.MSTeamsAuthentication;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
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

public final class GsonSupplier {

  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(MSTeamsAuthentication.class, "type")
                  .registerSubtype(BearerAuthentication.class, "token")
                  .registerSubtype(ClientSecretAuthentication.class, "clientCredentials")
                  .registerSubtype(RefreshTokenAuthentication.class, "refresh"))
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(MSTeamsRequestData.class, "method")
                  // channel
                  .registerSubtype(CreateChannel.class, "createChannel")
                  .registerSubtype(GetChannel.class, "getChannel")
                  .registerSubtype(ListChannels.class, "listAllChannels")
                  .registerSubtype(GetChannelMessage.class, "getMessageFromChannel")
                  .registerSubtype(SendMessageToChannel.class, "sendMessageToChannel")
                  .registerSubtype(ListChannelMessages.class, "listChannelMessages")
                  .registerSubtype(ListMessageRepliesInChannel.class, "listMessageRepliesInChannel")
                  .registerSubtype(ListChannelMembers.class, "listMembersInChannel")
                  // chat
                  .registerSubtype(CreateChat.class, "createChat")
                  .registerSubtype(GetChat.class, "getChat")
                  .registerSubtype(ListChats.class, "listChats")
                  .registerSubtype(GetMessageInChat.class, "getMessageFromChat")
                  .registerSubtype(ListChatMembers.class, "listMembersOfChat")
                  .registerSubtype(ListMessagesInChat.class, "listMessagesInChat")
                  .registerSubtype(SendMessageInChat.class, "sendMessageToChat"))
          .create();

  private GsonSupplier() {}

  public static Gson getGson() {
    return GSON;
  }
}
