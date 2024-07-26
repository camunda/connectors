/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.*;
import com.slack.api.model.Conversation;
import io.camunda.connector.slack.outbound.utils.DataLookupService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConversationsInviteDataTest {
  private static final String USERID = "testUserId";
  private static final String CHANNEL_NAME = "channel";
  private static final String CHANNEL_TYPE_ID = "channelId";
  private static final String CHANNEL_TYPE_NAME = "channelName";
  private static final String CHANNEL_ID = "id";
  @Mock private MethodsClient methodsClient;
  @Mock private UsersLookupByEmailResponse lookupByEmailResponse;
  @Mock private ConversationsListResponse conversationsListResponse;
  @Mock private User user;
  @Mock private ConversationsInviteResponse conversationsInviteResponse;
  @Captor private ArgumentCaptor<ConversationsInviteRequest> conversationsInviteRequest;

  @Test
  void invoke_shouldThrowExceptionWhenUserNotFoundByEmail() throws SlackApiException, IOException {
    // Given
    ConversationsInviteData conversationsInviteData =
        new ConversationsInviteData(
            CHANNEL_TYPE_NAME, CHANNEL_NAME, null, List.of("test1@test.com, test2@test.com"));
    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class))).thenReturn(null);
    // When and then
    Throwable thrown = catchThrowable(() -> conversationsInviteData.invoke(methodsClient));
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to find user with name or email : test1@test.com");
  }

  @Test
  void invoke_shouldThrowExceptionWhenNumberInputForUsers() {
    // Given number for users which is an invalid input type
    ConversationsInviteData conversationsInviteData =
        new ConversationsInviteData(CHANNEL_TYPE_NAME, CHANNEL_NAME, null, 1);
    // When and then
    Throwable thrown = catchThrowable(() -> conversationsInviteData.invoke(methodsClient));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid input type for users. Supported types are: List<String> and String");
  }

  @Test
  void invoke_shouldThrowExceptionWhenBooleanInputForUsers() {
    // Given boolean for users which is an invalid input type
    ConversationsInviteData conversationsInviteData =
        new ConversationsInviteData(CHANNEL_TYPE_NAME, CHANNEL_NAME, null, Boolean.TRUE);
    // When and then
    Throwable thrown = catchThrowable(() -> conversationsInviteData.invoke(methodsClient));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid input type for users. Supported types are: List<String> and String");
  }

  @Test
  void invoke_shouldThrowExceptionWhenIntegerListInputForUsers() {
    // Given List<Integer> for users which is an invalid input type
    ConversationsInviteData conversationsInviteData =
        new ConversationsInviteData(CHANNEL_TYPE_NAME, CHANNEL_NAME, null, Arrays.asList(1, 2));
    // When and then
    Throwable thrown = catchThrowable(() -> conversationsInviteData.invoke(methodsClient));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No user provided in a valid format");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "test1@test.com, test2@test.com",
        "firstName.LastName1@mail.org,firstName.LastName2@mail.org",
        "n.a.m.e@mail.ua",
        "a@m.uat",
        "_23@ma.au"
      })
  void invoke_shouldFindUserIdByEmail(final String emailList)
      throws SlackApiException, IOException {
    // Given
    ConversationsInviteData conversationsInviteData =
        new ConversationsInviteData(CHANNEL_TYPE_NAME, CHANNEL_NAME, null, emailList);

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(true);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);

    when(methodsClient.conversationsList(any(ConversationsListRequest.class)))
        .thenReturn(conversationsListResponse);
    when(conversationsListResponse.isOk()).thenReturn(true);
    when(conversationsListResponse.getChannels())
        .thenReturn(
            Arrays.asList(
                Conversation.builder().id("wrong_id").name("wrong_name").build(),
                Conversation.builder().id("good_id").name(CHANNEL_NAME).build()));
    when(conversationsListResponse.getResponseMetadata()).thenReturn(new ResponseMetadata());

    when(methodsClient.conversationsInvite(conversationsInviteRequest.capture()))
        .thenReturn(conversationsInviteResponse);
    when(conversationsInviteResponse.isOk()).thenReturn(true);
    when(conversationsInviteResponse.getChannel()).thenReturn(new Conversation());
    when(conversationsInviteResponse.getNeeded()).thenReturn("needed");
    when(conversationsInviteResponse.getProvided()).thenReturn("provided");
    // When
    conversationsInviteData.invoke(methodsClient);
    // Then
    ConversationsInviteRequest value = conversationsInviteRequest.getValue();
    List<String> expectedResolvedUserIds =
        Arrays.stream(emailList.split(",")).map(s -> USERID).collect(Collectors.toList());

    assertEquals("good_id", value.getChannel());
    assertTrue(
        value.getUsers().containsAll(expectedResolvedUserIds)
            && expectedResolvedUserIds.containsAll(value.getUsers()));
  }

  @Test
  void invoke_shouldNotLookUpForChannelNames() throws SlackApiException, IOException {

    try (MockedStatic<DataLookupService> dataLookupService = mockStatic(DataLookupService.class)) {

      List<String> mails = List.of("test1@test.com, test2@test.com");
      ConversationsInviteData conversationsInviteData =
          new ConversationsInviteData(CHANNEL_TYPE_ID, null, CHANNEL_ID, mails);

      dataLookupService
          .when(() -> DataLookupService.getUserIdsFromUsers(eq(mails), any()))
          .thenReturn(List.of("id1", "id2"));

      when(methodsClient.conversationsInvite(conversationsInviteRequest.capture()))
          .thenReturn(conversationsInviteResponse);
      when(conversationsInviteResponse.isOk()).thenReturn(true);
      when(conversationsInviteResponse.getChannel()).thenReturn(new Conversation());
      when(conversationsInviteResponse.getNeeded()).thenReturn("needed");
      when(conversationsInviteResponse.getProvided()).thenReturn("provided");
      // When
      conversationsInviteData.invoke(methodsClient);
      // Then
      ConversationsInviteRequest value = conversationsInviteRequest.getValue();
      assertEquals("id", value.getChannel());
      dataLookupService.verify(() -> DataLookupService.getChannelIdByName(any(), any()), never());
    }
  }
}
