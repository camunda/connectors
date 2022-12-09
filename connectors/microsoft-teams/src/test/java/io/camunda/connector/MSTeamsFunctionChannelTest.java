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

import com.microsoft.graph.models.Channel;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.requests.ChannelCollectionReferenceRequest;
import com.microsoft.graph.requests.ChannelCollectionRequest;
import com.microsoft.graph.requests.ChannelCollectionRequestBuilder;
import com.microsoft.graph.requests.ChannelCollectionResponse;
import com.microsoft.graph.requests.ChannelCollectionWithReferencesPage;
import com.microsoft.graph.requests.ChannelCollectionWithReferencesRequestBuilder;
import com.microsoft.graph.requests.ChannelRequest;
import com.microsoft.graph.requests.ChannelRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionResponse;
import com.microsoft.graph.requests.ChatMessageRequest;
import com.microsoft.graph.requests.ChatMessageRequestBuilder;
import com.microsoft.graph.requests.ConversationMemberCollectionPage;
import com.microsoft.graph.requests.ConversationMemberCollectionRequest;
import com.microsoft.graph.requests.ConversationMemberCollectionRequestBuilder;
import com.microsoft.graph.requests.ConversationMemberCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.TeamRequestBuilder;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
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
class MSTeamsFunctionChannelTest extends BaseTest {

  private MSTeamsFunction function;
  @Mock private GraphServiceClientSupplier graphServiceClientSupplier;
  private OutboundConnectorContext context;
  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private TeamRequestBuilder teamRequestBuilder;
  @Mock private ChannelCollectionRequestBuilder channelCollectionRequestBuilder;
  @Mock private ChannelCollectionRequest channelCollectionRequest;
  @Mock private ChannelRequestBuilder channelRequestBuilder;
  @Mock private ChannelRequest channelRequest;
  @Mock private ChatMessageRequestBuilder chatMessageRequestBuilder;
  @Mock private ChatMessageRequest chatMessageRequest;
  @Mock private ChatMessage chatMessage;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;

  @Mock
  private ConversationMemberCollectionRequestBuilder conversationMemberCollectionRequestBuilder;

  @Mock private ConversationMemberCollectionRequest conversationMemberCollectionRequest;

  @Mock
  private ChannelCollectionWithReferencesRequestBuilder
      channelCollectionWithReferencesRequestBuilder;

  @Mock private ChannelCollectionReferenceRequest channelCollectionReferenceRequest;

  @BeforeEach
  public void init() {
    function = new MSTeamsFunction(graphServiceClientSupplier, gson);

    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(
            any(ClientSecretAuthentication.class)))
        .thenReturn(graphServiceClient);
    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(
            ActualValue.Authentication.BEARER_TOKEN))
        .thenReturn(graphServiceClient);

    when(graphServiceClient.teams(ActualValue.Channel.GROUP_ID)).thenReturn(teamRequestBuilder);
    // crete Channel
    when(teamRequestBuilder.channels()).thenReturn(channelCollectionRequestBuilder);
    when(channelCollectionRequestBuilder.buildRequest()).thenReturn(channelCollectionRequest);
    when(channelCollectionRequest.post(any(Channel.class))).thenReturn(new Channel());
    // get Channel
    when(teamRequestBuilder.channels(ActualValue.Channel.CHANNEL_ID))
        .thenReturn(channelRequestBuilder);
    when(channelRequestBuilder.buildRequest()).thenReturn(channelRequest);
    when(channelRequest.get()).thenReturn(new Channel());
    // get channel message by id
    when(channelRequestBuilder.messages(ActualValue.Channel.MESSAGE_ID))
        .thenReturn(chatMessageRequestBuilder);
    when(chatMessageRequestBuilder.buildRequest()).thenReturn(chatMessageRequest);
    when(chatMessageRequest.get()).thenReturn(chatMessage);
    // list channel messages
    when(channelRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    when(chatMessageCollectionRequest.get())
        .thenReturn(new ChatMessageCollectionPage(new ChatMessageCollectionResponse(), null));
    // list channel members
    when(channelRequestBuilder.members()).thenReturn(conversationMemberCollectionRequestBuilder);
    when(conversationMemberCollectionRequestBuilder.buildRequest())
        .thenReturn(conversationMemberCollectionRequest);
    when(conversationMemberCollectionRequest.get())
        .thenReturn(
            new ConversationMemberCollectionPage(new ConversationMemberCollectionResponse(), null));
    // list all channels
    when(teamRequestBuilder.allChannels())
        .thenReturn(channelCollectionWithReferencesRequestBuilder);
    when(channelCollectionWithReferencesRequestBuilder.buildRequest())
        .thenReturn(channelCollectionReferenceRequest);
    when(channelCollectionReferenceRequest.filter(ActualValue.Channel.FILTER))
        .thenReturn(channelCollectionReferenceRequest);
    when(channelCollectionReferenceRequest.get())
        .thenReturn(new ChannelCollectionWithReferencesPage(new ChannelCollectionResponse(), null));
    // listMessageRepliesInChannel
    when(chatMessageRequestBuilder.replies()).thenReturn(chatMessageCollectionRequestBuilder);
    // send message to channel
    when(chatMessageCollectionRequest.post(any(ChatMessage.class))).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("executeSuccessWorkWithChannelTestCases")
  public void execute_shouldExecuteAndReturnResponse(String input) {
    context = getContextBuilderWithSecrets().variables(input).build();
    Object execute = function.execute(context);
    assertThat(execute).isNotNull();
  }
}
