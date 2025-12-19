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
import com.microsoft.graph.models.ChannelCollectionResponse;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ChatMessageCollectionResponse;
import com.microsoft.graph.models.ConversationMemberCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.teams.TeamsRequestBuilder;
import com.microsoft.graph.teams.item.TeamItemRequestBuilder;
import com.microsoft.graph.teams.item.allchannels.AllChannelsRequestBuilder;
import com.microsoft.graph.teams.item.channels.ChannelsRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.ChannelItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.members.MembersRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.messages.item.ChatMessageItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.messages.item.replies.RepliesRequestBuilder;
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
class MSTeamsFunctionChannelTest extends BaseTest {

  private MSTeamsFunction function;
  @Mock private GraphServiceClientSupplier graphServiceClientSupplier;
  @Mock private GraphServiceClient graphServiceClient;
  @Mock private TeamsRequestBuilder teamsRequestBuilder;
  @Mock private TeamItemRequestBuilder teamItemRequestBuilder;
  @Mock private ChannelsRequestBuilder channelsRequestBuilder;
  @Mock private ChannelItemRequestBuilder channelItemRequestBuilder;
  @Mock private MessagesRequestBuilder messagesRequestBuilder;
  @Mock private ChatMessageItemRequestBuilder chatMessageRequestBuilder;
  @Mock private AllChannelsRequestBuilder allChannelsRequestBuilder;
  @Mock private ChatMessage chatMessage;
  @Mock private MembersRequestBuilder membersRequestBuilder;
  @Mock private RepliesRequestBuilder repliesRequestBuilder;

  @BeforeEach
  public void init() {
    function = new MSTeamsFunction(graphServiceClientSupplier);

    when(graphServiceClientSupplier.buildAndGetGraphServiceClient(any(MSTeamsAuthentication.class)))
        .thenReturn(graphServiceClient);

    when(graphServiceClient.teams()).thenReturn(teamsRequestBuilder);
    when(teamsRequestBuilder.byTeamId(ActualValue.Channel.GROUP_ID))
        .thenReturn(teamItemRequestBuilder);
    // crete Channel
    when(teamItemRequestBuilder.channels()).thenReturn(channelsRequestBuilder);
    when(channelsRequestBuilder.post(any(Channel.class))).thenReturn(new Channel());
    // get Channel
    when(channelsRequestBuilder.byChannelId(ActualValue.Channel.CHANNEL_ID))
        .thenReturn(channelItemRequestBuilder);
    when(channelItemRequestBuilder.get()).thenReturn(new Channel());
    // get channel message by id
    when(channelItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    when(messagesRequestBuilder.byChatMessageId(ActualValue.Channel.MESSAGE_ID))
        .thenReturn(chatMessageRequestBuilder);
    when(chatMessageRequestBuilder.get()).thenReturn(chatMessage);
    // list channel messages
    when(messagesRequestBuilder.get(any())).thenReturn(new ChatMessageCollectionResponse());
    // list channel members
    when(channelItemRequestBuilder.members()).thenReturn(membersRequestBuilder);
    when(membersRequestBuilder.get()).thenReturn(new ConversationMemberCollectionResponse());
    // list all channels
    when(teamItemRequestBuilder.allChannels()).thenReturn(allChannelsRequestBuilder);
    when(allChannelsRequestBuilder.get(any())).thenReturn(new ChannelCollectionResponse());
    // listMessageRepliesInChannel
    when(chatMessageRequestBuilder.replies()).thenReturn(repliesRequestBuilder);
    when(repliesRequestBuilder.get()).thenReturn(new ChatMessageCollectionResponse());
    // send message to channel
    when(messagesRequestBuilder.post(any(ChatMessage.class))).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("executeSuccessWorkWithChannelTestCases")
  public void execute_shouldExecuteAndReturnResponse(String input) {
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    Object execute = function.execute(context);
    assertThat(execute).isNotNull();
  }
}
