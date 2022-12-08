/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsCreateRequest;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import com.slack.api.model.User;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.slack.model.*;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SlackFunctionTest extends BaseTest {

  @Mock private MethodsClient methodsClient;
  @Mock private UsersListResponse usersListResponse;
  @Mock private ConversationsListResponse conversationsListResponse;
  @Mock private UsersLookupByEmailResponse lookupByEmailResponse;
  @Mock private ResponseMetadata responseMetadata;
  @Mock private User user;
  @Mock private Conversation channel;
  @Mock private ChatPostMessageResponse chatPostMessageResponse;
  @Mock private ConversationsCreateResponse conversationsCreateResponse;
  @Mock private ConversationsInviteResponse conversationsInviteResponse;
  @Mock private Slack slackClientMock;

  @Captor private ArgumentCaptor<ChatPostMessageRequest> chatPostMessageRequestArgumentCaptor;

  @Captor
  private ArgumentCaptor<ConversationsCreateRequest> conversationsCreateRequestArgumentCaptor;

  @Captor
  private ArgumentCaptor<ConversationsInviteRequest> conversationsInviteRequestArgumentCaptor;

  @Captor private ArgumentCaptor<UsersLookupByEmailRequest> usersLookupByEmailRequestArgumentCaptor;

  private SlackFunction slackFunction;
  private OutboundConnectorContext context;

  @BeforeEach
  public void init() throws SlackApiException, IOException {
    slackFunction = new SlackFunction(slackClientMock, gson);

    when(slackClientMock.methods(ActualValue.TOKEN)).thenReturn(methodsClient);
    when(methodsClient.usersLookupByEmail(usersLookupByEmailRequestArgumentCaptor.capture()))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);

    when(methodsClient.usersList(any(UsersListRequest.class))).thenReturn(usersListResponse);
    when(responseMetadata.getNextCursor()).thenReturn(null);
    when(usersListResponse.getMembers()).thenReturn(List.of(user));
    when(usersListResponse.isOk()).thenReturn(Boolean.TRUE);
    when(usersListResponse.getResponseMetadata()).thenReturn(responseMetadata);

    when(user.getRealName()).thenReturn(ActualValue.USER_REAL_NAME);
    when(user.getId()).thenReturn(ActualValue.USER_ID);

    when(methodsClient.chatPostMessage(chatPostMessageRequestArgumentCaptor.capture()))
        .thenReturn(chatPostMessageResponse);

    when(chatPostMessageResponse.isOk()).thenReturn(Boolean.TRUE);
    when(chatPostMessageResponse.getTs()).thenReturn(ActualValue.TS);
    when(chatPostMessageResponse.getChannel()).thenReturn(ActualValue.USER_ID);
    when(chatPostMessageResponse.getMessage()).thenReturn(new Message());

    // create channel
    when(methodsClient.conversationsCreate(conversationsCreateRequestArgumentCaptor.capture()))
        .thenReturn(conversationsCreateResponse);

    when(conversationsCreateResponse.isOk()).thenReturn(Boolean.TRUE);
    when(conversationsCreateResponse.getChannel()).thenReturn(new Conversation());

    // invite tot channel
    when(methodsClient.conversationsInvite(conversationsInviteRequestArgumentCaptor.capture()))
        .thenReturn(conversationsInviteResponse);

    when(conversationsInviteResponse.isOk()).thenReturn(Boolean.TRUE);
    when(conversationsInviteResponse.getChannel()).thenReturn(new Conversation());

    when(methodsClient.conversationsList(any(ConversationsListRequest.class)))
        .thenReturn(conversationsListResponse);
    when(responseMetadata.getNextCursor()).thenReturn(null);
    when(conversationsListResponse.getChannels()).thenReturn(List.of(channel));
    when(conversationsListResponse.isOk()).thenReturn(Boolean.TRUE);
    when(conversationsListResponse.getResponseMetadata()).thenReturn(responseMetadata);

    when(channel.getName()).thenReturn(ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME);
    when(channel.getId()).thenReturn(ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME);
  }

  @ParameterizedTest
  @MethodSource("executeWithEmailTestCases")
  public void execute_shouldSendPostMessageByEmail(String input) throws Exception {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    Object executeResponse = slackFunction.execute(context);
    // Then
    assertThat(usersLookupByEmailRequestArgumentCaptor.getValue().getEmail())
        .isEqualTo(ActualValue.ChatPostMessageData.EMAIL);
    assertThat(chatPostMessageRequestArgumentCaptor.getValue().getChannel())
        .isEqualTo(ActualValue.USER_ID);

    assertThatSlackResponseIsCorrect(executeResponse);
  }

  @ParameterizedTest
  @MethodSource("executeWithUserNameTestCases")
  public void execute_shouldSendPostMessageWithUserName(String input) throws Exception {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    Object executeResponse = slackFunction.execute(context);
    // Then
    assertThat(chatPostMessageRequestArgumentCaptor.getValue().getChannel())
        .isEqualTo(ActualValue.USER_ID);

    assertThatSlackResponseIsCorrect(executeResponse);
  }

  @ParameterizedTest
  @MethodSource("executeWithChannelNameTestCases")
  public void execute_shouldSendPostMessageWithChannelName(String input) throws Exception {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    Object executeResponse = slackFunction.execute(context);
    // Then
    assertThat(chatPostMessageRequestArgumentCaptor.getValue().getChannel())
        .isEqualTo(ActualValue.ChatPostMessageData.CHANNEL_NAME);

    assertThatSlackResponseIsCorrect(executeResponse);
  }

  private static void assertThatSlackResponseIsCorrect(final Object executeResponse) {
    assertThat(executeResponse).isInstanceOf(ChatPostMessageSlackResponse.class);
    ChatPostMessageSlackResponse slackResponse = (ChatPostMessageSlackResponse) executeResponse;
    assertThat(slackResponse.getChannel()).isEqualTo(ActualValue.USER_ID);
    assertThat(slackResponse.getTs()).isEqualTo(ActualValue.TS);
    assertThat(slackResponse.getMessage()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("executeCreateChannelTestCases")
  public void execute_shouldCreateNewChannel(String input) throws Exception {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    Object executeResponse = slackFunction.execute(context);
    // Then
    assertThat(conversationsCreateRequestArgumentCaptor.getValue().getName())
        .isEqualTo(ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME);

    SlackRequest<ConversationsCreateData> request = gson.fromJson(input, SlackRequest.class);
    assertThat(conversationsCreateRequestArgumentCaptor.getValue().isPrivate())
        .isEqualTo(request.getData().getVisibility() == ConversationsCreateData.Visibility.PRIVATE);
    assertThat(executeResponse).isInstanceOf(ConversationsCreateSlackResponse.class);
    ConversationsCreateSlackResponse response = (ConversationsCreateSlackResponse) executeResponse;
    assertThat(response.getChannel()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("executeInviteToChannelTestCases")
  public void execute_shouldInviteToChannel(String input) throws Exception {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    // When
    Object executeResponse = slackFunction.execute(context);
    // Then
    assertThat(conversationsInviteRequestArgumentCaptor.getValue().getChannel())
        .isEqualTo(ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME);

    SlackRequest<ConversationsInviteData> request = gson.fromJson(input, SlackRequest.class);
    assertThat(executeResponse).isInstanceOf(ConversationsInviteSlackResponse.class);
    ConversationsInviteSlackResponse response = (ConversationsInviteSlackResponse) executeResponse;
    assertThat(response.getChannel()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("executeWithUserNameTestCases")
  void execute_shouldThrowExceptionWhenUserListResponseIsFail(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(usersListResponse.isOk()).thenReturn(Boolean.FALSE);
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("Unable to find user with name: JohnDou");
  }

  @ParameterizedTest
  @MethodSource("executeWithUserNameTestCases")
  void execute_shouldThrowExceptionWhenUserNameNotFound(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(user.getRealName()).thenReturn("");
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("Unable to find user with name: JohnDou");
  }

  @ParameterizedTest
  @MethodSource("executeWithUserNameTestCases")
  void execute_shouldThrowExceptionWhenUserIdIsNull(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(user.getId()).thenReturn(null);
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("Unable to find user with name: JohnDou");
  }

  @ParameterizedTest
  @MethodSource("executeCreateChannelTestCases")
  void execute_shouldThrowExceptionWhenConversationsCreateResponseFail(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(conversationsCreateResponse.isOk()).thenReturn(Boolean.FALSE);
    when(conversationsCreateResponse.getError()).thenReturn("error string");
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("error string");
  }

  @ParameterizedTest
  @MethodSource("executeWithChannelNameTestCases")
  void execute_shouldThrowExceptionWhenChatPostMessageResponseFail(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(chatPostMessageResponse.isOk()).thenReturn(Boolean.FALSE);
    when(chatPostMessageResponse.getError()).thenReturn("error string");
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("error string");
  }

  @ParameterizedTest
  @MethodSource("executeWithEmailTestCases")
  void execute_shouldThrowExceptionWhenEmailNotFound(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(lookupByEmailResponse.getUser()).thenReturn(null);
    // When and then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> slackFunction.execute(context),
            "RuntimeException was expected");
    assertThat(thrown.getMessage())
        .contains(
            "User with email john.dou@camundamail.com not found; or unable 'users:read.email' permission");
  }

  @ParameterizedTest
  @MethodSource("fromJsonFailTestCases")
  void execute_shouldThrowExceptionRequestMethodIsWrong(String input) {
    // Given
    context = getContextBuilderWithSecrets().variables(input).build();
    when(lookupByEmailResponse.getUser()).thenReturn(null);
    // When and then
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> slackFunction.execute(context),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("The object to be validated must not be null");
  }
}
