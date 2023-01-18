/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.requests.ChatCollectionPage;
import com.microsoft.graph.requests.ChatCollectionRequest;
import com.microsoft.graph.requests.ChatCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatMessageRequest;
import com.microsoft.graph.requests.ChatMessageRequestBuilder;
import com.microsoft.graph.requests.ChatRequest;
import com.microsoft.graph.requests.ChatRequestBuilder;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionRequest;
import com.microsoft.graph.requests.ConversationMemberCollectionRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MSTeamsFunctionChatTest extends BaseTest {

  private MSTeamsFunction function;
  @Mock private GraphServiceClientSupplier graphServiceClientSupplier;
  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private ChatCollectionRequestBuilder chatCollectionRequestBuilder;
  @Mock private ChatCollectionRequest chatCollectionRequest;
  @Mock private ChatRequestBuilder chatRequestBuilder;
  @Mock private ChatRequest chatRequest;
  @Mock private ChatMessageRequestBuilder chatMessageRequestBuilder;
  @Mock private ChatMessageRequest chatMessageRequest;

  @Mock
  private ConversationMemberCollectionRequestBuilder conversationMemberCollectionRequestBuilder;

  @Mock private ConversationMemberCollectionRequest conversationMemberCollectionRequest;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;

  @BeforeEach
  public void init() {
    function = new MSTeamsFunction(graphServiceClientSupplier, gson);

    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(
            any(ClientSecretAuthentication.class)))
        .thenReturn(graphServiceClient);
    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(
            ActualValue.Authentication.BEARER_TOKEN))
        .thenReturn(graphServiceClient);
    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(
            any(RefreshTokenAuthentication.class)))
        .thenReturn(graphServiceClient);

    // create chat
    when(graphServiceClient.chats()).thenReturn(chatCollectionRequestBuilder);
    when(chatCollectionRequestBuilder.buildRequest()).thenReturn(chatCollectionRequest);
    when(chatCollectionRequest.post(any(Chat.class))).thenReturn(new Chat());
    // get chat
    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.buildRequest()).thenReturn(chatRequest);
    when(chatRequest.expand("members")).thenReturn(chatRequest);
    when(chatRequest.get()).thenReturn(new Chat());
    // list chats
    when(chatCollectionRequest.get()).thenReturn(new ChatCollectionPage(List.of(new Chat()), null));
    // get message in chat
    when(chatRequestBuilder.messages(ActualValue.Chat.MESSAGE_ID))
        .thenReturn(chatMessageRequestBuilder);
    when(chatMessageRequestBuilder.buildRequest()).thenReturn(chatMessageRequest);
    when(chatMessageRequest.get()).thenReturn(new ChatMessage());
    // list members
    when(chatRequestBuilder.members()).thenReturn(conversationMemberCollectionRequestBuilder);
    when(conversationMemberCollectionRequestBuilder.buildRequest())
        .thenReturn(conversationMemberCollectionRequest);
    when(conversationMemberCollectionRequest.get())
        .thenReturn(new ConversationMemberCollectionPage(new ArrayList<>(), null));
    // list messages
    when(chatRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    when(chatMessageCollectionRequest.get())
        .thenReturn(new ChatMessageCollectionPage(new ArrayList<>(), null));
    // send message in chat
    when(chatMessageCollectionRequest.post(any(ChatMessage.class))).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("executeSuccessWorkWithChatTestCases")
  public void execute_shouldExecuteAndReturnResponse(String input) {
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    Object execute = function.execute(context);
    assertThat(execute).isNotNull();
  }
}
