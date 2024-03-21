/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.authentication.BearerAuthentication;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.model.authentication.MSTeamsAuthentication;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
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
  public void test(String input) throws JsonProcessingException {

    objectMapper.readTree(input).get("authentication").get("type").asText();

    String authType = objectMapper.readTree(input).get("authentication").get("type").asText();
    String methodType = objectMapper.readTree(input).get("data").get("method").asText();

    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);

    assertThat(request.authentication()).isInstanceOf(authMap.get(authType));
    assertThat(request.data()).isInstanceOf(methodsMap.get(methodType));
  }
}
