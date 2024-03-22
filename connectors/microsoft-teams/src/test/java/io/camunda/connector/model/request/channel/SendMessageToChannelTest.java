/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.teams.TeamsRequestBuilder;
import com.microsoft.graph.teams.item.TeamItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.ChannelsRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.ChannelItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.messages.MessagesRequestBuilder;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendMessageToChannelTest extends BaseTest {
  private SendMessageToChannel sendMessageToChannel;

  @Mock private GraphServiceClient graphServiceClient;

  @Mock private TeamsRequestBuilder teamsRequestBuilder;
  @Mock private TeamItemRequestBuilder teamItemRequestBuilder;
  @Mock private ChannelsRequestBuilder channelsRequestBuilder;
  @Mock private ChannelItemRequestBuilder channelItemRequestBuilder;
  @Mock private MessagesRequestBuilder messagesRequestBuilder;

  @Captor private ArgumentCaptor<ChatMessage> chatMessageArgumentCaptor;

  @BeforeEach
  public void init() {

    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID, ActualValue.Channel.CHANNEL_ID, "channel content", null);

    when(graphServiceClient.teams()).thenReturn(teamsRequestBuilder);
    when(teamsRequestBuilder.byTeamId(ActualValue.Channel.GROUP_ID))
        .thenReturn(teamItemRequestBuilder);
    when(teamItemRequestBuilder.channels()).thenReturn(channelsRequestBuilder);
    when(channelsRequestBuilder.byChannelId(ActualValue.Channel.CHANNEL_ID))
        .thenReturn(channelItemRequestBuilder);
    when(channelItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    when(messagesRequestBuilder.post(chatMessageArgumentCaptor.capture()))
        .thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("sendMessageToChannelValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetTextBodyTypeByDefault() {
    // Given SendMessageInChat without bodyType
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("channel content");
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Text);
  }

  @ParameterizedTest
  @ValueSource(strings = {"html", "HTML", "text", "TexT"})
  public void invoke_shouldSetTextBodyType(String input) {
    // Given
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID, ActualValue.Channel.CHANNEL_ID, "channel content", input);
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("channel content");
    assertThat(chatMessage.getBody().getContentType().toString().toLowerCase())
        .isEqualTo(input.toLowerCase());
  }
}
