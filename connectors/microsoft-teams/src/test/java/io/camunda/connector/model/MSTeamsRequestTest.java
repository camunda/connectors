/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import io.camunda.connector.BaseTest;
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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MSTeamsRequestTest extends BaseTest {

  private Map<String, Class<? extends MSTeamsRequestData>> methodsMap;
  private Map<String, Class<? extends MSTeamsAuthentication>> authMap;

  @BeforeEach
  public void init() {
    authMap = new HashMap<>();
    authMap.put("token", BearerAuthentication.class);
    authMap.put("clientCredentials", ClientSecretAuthentication.class);
    authMap.put("refresh", RefreshTokenAuthentication.class);

    methodsMap = new HashMap<>();
    // channel
    methodsMap.put("createChannel", CreateChannel.class);
    methodsMap.put("getChannel", GetChannel.class);
    methodsMap.put("listAllChannels", ListChannels.class);
    methodsMap.put("getMessageFromChannel", GetChannelMessage.class);
    methodsMap.put("sendMessageToChannel", SendMessageToChannel.class);
    methodsMap.put("listChannelMessages", ListChannelMessages.class);
    methodsMap.put("listMessageRepliesInChannel", ListMessageRepliesInChannel.class);
    methodsMap.put("listMembersInChannel", ListChannelMembers.class);
    // chat
    methodsMap.put("createChat", CreateChat.class);
    methodsMap.put("getChat", GetChat.class);
    methodsMap.put("listChats", ListChats.class);
    methodsMap.put("getMessageFromChat", GetMessageInChat.class);
    methodsMap.put("listMembersOfChat", ListChatMembers.class);
    methodsMap.put("listMessagesInChat", ListMessagesInChat.class);
    methodsMap.put("sendMessageToChat", SendMessageInChat.class);
  }

  @ParameterizedTest
  @MethodSource("parseRequestTestCases")
  public void test(String input) {
    JsonObject jsonObject = gson.fromJson(input, JsonObject.class);
    String authType = jsonObject.get("authentication").getAsJsonObject().get("type").getAsString();
    String methodType = jsonObject.get("data").getAsJsonObject().get("method").getAsString();

    MSTeamsRequest request = gson.fromJson(input, MSTeamsRequest.class);

    assertThat(request.getAuthentication()).isInstanceOf(authMap.get(authType));
    assertThat(request.getData()).isInstanceOf(methodsMap.get(methodType));
  }
}
