/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatPostMessageDataTest {

  @Mock private MethodsClient methodsClient;
  @Mock private UsersLookupByEmailResponse lookupByEmailResponse;
  @Mock private User user;
  @Mock private ChatPostMessageResponse chatPostMessageResponse;

  @Captor private ArgumentCaptor<ChatPostMessageRequest> chatPostMessageRequest;

  private static final String USERID = "testUserId";
  private static final JsonNode EMPTY_JSON;

  static {
    try {
      EMPTY_JSON = ConnectorsObjectMapperSupplier.getCopy().readTree("{}");
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void invoke_shouldThrowExceptionWhenUserWithoutEmail() throws SlackApiException, IOException {
    // Given
    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData("test@test.com", "thread_ts", "plainText", "Test text", EMPTY_JSON);
    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class))).thenReturn(null);
    // When and then
    Throwable thrown = catchThrowable(() -> chatPostMessageData.invoke(methodsClient));
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(
            "User with email test@test.com not found; or unable 'users:read.email' permission");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "test@test.com",
        "firstName.LastName@mail.org",
        "n.a.m.e@mail.ua",
        "a@m.uat",
        "_23@ma.au"
      })
  void invoke_shouldFindUserIdByEmail(String email) throws SlackApiException, IOException {
    // Given
    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData(email, "thread_ts", "plainText", "test", null);

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);
    when(methodsClient.chatPostMessage(chatPostMessageRequest.capture()))
        .thenReturn(chatPostMessageResponse);
    when(chatPostMessageResponse.isOk()).thenReturn(true);
    when(chatPostMessageResponse.getMessage()).thenReturn(new Message());
    // When
    chatPostMessageData.invoke(methodsClient);
    // Then
    ChatPostMessageRequest value = chatPostMessageRequest.getValue();
    assertThat(value.getChannel()).isEqualTo(USERID);
  }

  @Test
  void invoke_WhenTextIsGiven_ShouldInvoke() throws SlackApiException, IOException {
    // Given
    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData("test@test.com", "thread_ts", "plainText", "test", null);

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);
    when(methodsClient.chatPostMessage(chatPostMessageRequest.capture()))
        .thenReturn(chatPostMessageResponse);
    when(chatPostMessageResponse.isOk()).thenReturn(true);
    when(chatPostMessageResponse.getMessage()).thenReturn(new Message());
    // When
    chatPostMessageData.invoke(methodsClient);
    // Then
    ChatPostMessageRequest value = chatPostMessageRequest.getValue();
    assertThat(value.getChannel()).isEqualTo(USERID);
  }

  @Test
  void invoke_WhenContentBlockIsGiven_ShouldInvoke() throws SlackApiException, IOException {
    // Given
    final var blockContent =
        """
        [
        	{
        		"type": "header",
        		"text": {
        			"type": "plain_text",
        			"text": "New request"
        		}
        	},
        	{
        		"type": "section",
        		"fields": [
        			{
        				"type": "mrkdwn",
        				"text": "*Type:*\\nPaid Time Off"
        			},
        			{
        				"type": "mrkdwn",
        				"text": "*Created by:*\\n<example.com|Fred Enriquez>"
        			}
        		]
        	},
        	{
        		"type": "section",
        		"fields": [
        			{
        				"type": "mrkdwn",
        				"text": "*When:*\\nAug 10 - Aug 13"
        			}
        		]
        	},
        	{
        		"type": "section",
        		"text": {
        			"type": "mrkdwn",
        			"text": "<https://example.com|View request>"
        		}
        	}
        ]
        """;

    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();

    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData(
            "test@test.com",
            "thread_ts",
            "messageBlock",
            "test",
            objectMapper.readTree(blockContent));

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);
    when(methodsClient.chatPostMessage(chatPostMessageRequest.capture()))
        .thenReturn(chatPostMessageResponse);
    when(chatPostMessageResponse.isOk()).thenReturn(true);
    when(chatPostMessageResponse.getMessage()).thenReturn(new Message());
    // When
    chatPostMessageData.invoke(methodsClient);
    // Then
    ChatPostMessageRequest value = chatPostMessageRequest.getValue();
    assertThat(value.getChannel()).isEqualTo(USERID);
  }

  @Test
  void invoke_WhenContentBlockIsNotArray_ShouldThrow() throws SlackApiException, IOException {
    // Given
    final var blockContent =
        """
        {
           "type": "section",
           "text": {
             "type": "mrkdwn",
             "text": "New Paid Time Off request from <example.com|Fred Enriquez>\\n\\n<https://example.com|View request>"
           }
         }
        """;

    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();

    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData(
            "test@test.com", "thread_ts", "plainText", "test", objectMapper.readTree(blockContent));

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);
    // When
    Throwable thrown = catchThrowable(() -> chatPostMessageData.invoke(methodsClient));
    // Then

    assertThat(thrown).hasMessageContaining("Block section must be an array");
    assertThat(thrown).isInstanceOf(ConnectorException.class);
  }
}
