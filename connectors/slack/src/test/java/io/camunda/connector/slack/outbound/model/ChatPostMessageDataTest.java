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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.files.FilesCompleteUploadExternalRequest;
import com.slack.api.methods.request.files.FilesGetUploadURLExternalRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.files.FilesCompleteUploadExternalResponse;
import com.slack.api.methods.response.files.FilesGetUploadURLExternalResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.File;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import com.slack.api.util.http.SlackHttpClient;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.document.CamundaDocument;
import io.camunda.document.DocumentMetadataImpl;
import io.camunda.document.store.CamundaDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatPostMessageDataTest {

  private static final String USERID = "testUserId";
  private static final JsonNode EMPTY_JSON;

  static {
    try {
      EMPTY_JSON = ConnectorsObjectMapperSupplier.getCopy().readTree("{}");
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private MethodsClient methodsClient;

  @Mock private UsersLookupByEmailResponse lookupByEmailResponse;
  @Mock private User user;
  @Mock private ChatPostMessageResponse chatPostMessageResponse;
  @Captor private ArgumentCaptor<ChatPostMessageRequest> chatPostMessageRequest;

  @Test
  void invoke_shouldThrowExceptionWhenUserWithoutEmail() throws SlackApiException, IOException {
    // Given
    ChatPostMessageData chatPostMessageData =
        new ChatPostMessageData(
            "test@test.com",
            "thread_ts",
            MessageType.plainText,
            "Test text",
            EMPTY_JSON,
            List.of());
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
        new ChatPostMessageData(email, "thread_ts", MessageType.plainText, "test", null, List.of());

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
        new ChatPostMessageData(
            "test@test.com",
            "thread_ts",
            MessageType.plainText,
            "test",
            null,
            List.of(prepareDocument()));

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);

    // mock File uploading
    mockGetExternalURL();

    OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class, Answers.RETURNS_DEEP_STUBS);

    try (MockedStatic<SlackHttpClient> slackHttpClient =
        Mockito.mockStatic(SlackHttpClient.class)) {
      slackHttpClient.when(() -> SlackHttpClient.buildOkHttpClient(any())).thenReturn(okHttpClient);

      var response = mock(Response.class);
      when(response.code()).thenReturn(200);

      when(okHttpClient.newCall(any(Request.class)).execute()).thenReturn(response);

      mockCompleteExternalURL();

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
            MessageType.messageBlock,
            "test",
            objectMapper.readTree(blockContent),
            List.of());

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
            "test@test.com",
            "thread_ts",
            MessageType.plainText,
            "test",
            objectMapper.readTree(blockContent),
            List.of());

    when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
        .thenReturn(lookupByEmailResponse);
    when(lookupByEmailResponse.isOk()).thenReturn(Boolean.TRUE);
    when(lookupByEmailResponse.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(USERID);
    // When
    Throwable thrown = catchThrowable(() -> chatPostMessageData.invoke(methodsClient));
    // Then

    assertThat(thrown).hasMessageContaining("Block section must be an array");
    assertThat(thrown).isInstanceOf(ConnectorInputException.class);
  }

  private Document prepareDocument() {
    DocumentReference.CamundaDocumentReference documentReference =
        Mockito.mock(DocumentReference.CamundaDocumentReference.class);
    CamundaDocumentStore documentStore = Mockito.mock(CamundaDocumentStore.class);

    var byteInput = new ByteArrayInputStream(new byte[0]);
    when(documentStore.getDocumentContent(any())).thenReturn(byteInput);

    DocumentMetadata documentMetadata =
        new DocumentMetadataImpl(
            "text/plain",
            OffsetDateTime.now().plusDays(1),
            3000L,
            "fileName.txt",
            "processId",
            2000L,
            Map.of());

    return new CamundaDocument(documentMetadata, documentReference, documentStore);
  }

  private void mockGetExternalURL() throws SlackApiException, IOException {
    var uploadURLResp = new FilesGetUploadURLExternalResponse();
    uploadURLResp.setOk(true);
    uploadURLResp.setUploadUrl("https:example.com");

    when(methodsClient.filesGetUploadURLExternal(any(FilesGetUploadURLExternalRequest.class)))
        .thenReturn(uploadURLResp);
  }

  private void mockCompleteExternalURL() throws SlackApiException, IOException {
    List<File> files = List.of(File.builder().id("id").build());
    FilesCompleteUploadExternalResponse completeUploadResp =
        new FilesCompleteUploadExternalResponse();
    completeUploadResp.setOk(true);
    completeUploadResp.setFiles(files);

    when(methodsClient.filesCompleteUploadExternal(any(FilesCompleteUploadExternalRequest.class)))
        .thenReturn(completeUploadResp);
  }
}
