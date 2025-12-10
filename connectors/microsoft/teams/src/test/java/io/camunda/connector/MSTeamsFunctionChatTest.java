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

import com.microsoft.graph.chats.ChatsRequestBuilder;
import com.microsoft.graph.chats.item.ChatItemRequestBuilder;
import com.microsoft.graph.chats.item.members.MembersRequestBuilder;
import com.microsoft.graph.chats.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.chats.item.messages.item.ChatMessageItemRequestBuilder;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatCollectionResponse;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageCollectionResponse;
import com.microsoft.graph.models.ConversationMemberCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.authentication.MSTeamsAuthentication;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
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
  @Mock private GraphServiceClient graphServiceClient;
  @Mock private ChatsRequestBuilder chatsRequestBuilder;
  @Mock private ChatItemRequestBuilder chatItemRequestBuilder;
  @Mock private MessagesRequestBuilder messagesRequestBuilder;
  @Mock private ChatMessageItemRequestBuilder chatMessageItemRequestBuilder;
  @Mock private MembersRequestBuilder membersRequestBuilder;

  @BeforeEach
  public void init() {
    function = new MSTeamsFunction(graphServiceClientSupplier);

    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(any(MSTeamsAuthentication.class)))
        .thenReturn(graphServiceClient);

    // create chat
    when(graphServiceClient.chats()).thenReturn(chatsRequestBuilder);
    when(chatsRequestBuilder.post(any(Chat.class))).thenReturn(new Chat());
    // get chat
    when(chatsRequestBuilder.byChatId(ActualValue.Chat.CHAT_ID)).thenReturn(chatItemRequestBuilder);
    when(chatItemRequestBuilder.get(any())).thenReturn(new Chat());
    // list chats
    when(chatsRequestBuilder.get()).thenReturn(new ChatCollectionResponse());
    // get message in chat
    when(chatItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    when(messagesRequestBuilder.byChatMessageId(ActualValue.Chat.MESSAGE_ID))
        .thenReturn(chatMessageItemRequestBuilder);
    when(chatMessageItemRequestBuilder.get()).thenReturn(new ChatMessage());

    // list members
    when(chatItemRequestBuilder.members()).thenReturn(membersRequestBuilder);
    when(membersRequestBuilder.get()).thenReturn(new ConversationMemberCollectionResponse());
    // list messages
    when(messagesRequestBuilder.get(any())).thenReturn(new ChatMessageCollectionResponse());
    // send message in chat
    when(messagesRequestBuilder.post(any(ChatMessage.class))).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("executeSuccessWorkWithChatTestCases")
  public void execute_shouldExecuteAndReturnResponse(String input) {
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    Object execute = function.execute(context);
    assertThat(execute).isNotNull();
  }
}
