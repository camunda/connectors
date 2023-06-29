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
import com.microsoft.graph.requests.ChannelRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.TeamRequestBuilder;
import io.camunda.connector.BaseTest;
import okhttp3.Request;
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

  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private TeamRequestBuilder teamRequestBuilder;
  @Mock private ChannelRequestBuilder channelRequestBuilder;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;

  @Captor private ArgumentCaptor<ChatMessage> messageCaptor;

  @BeforeEach
  public void init() {

    sendMessageToChannel = new SendMessageToChannel();
    sendMessageToChannel.setChannelId(ActualValue.Channel.CHANNEL_ID);
    sendMessageToChannel.setContent("channel content");
    sendMessageToChannel.setGroupId(ActualValue.Channel.GROUP_ID);

    when(graphServiceClient.teams(ActualValue.Channel.GROUP_ID)).thenReturn(teamRequestBuilder);
    when(teamRequestBuilder.channels(ActualValue.Channel.CHANNEL_ID))
        .thenReturn(channelRequestBuilder);

    when(channelRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(channelRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    when(chatMessageCollectionRequest.post(messageCaptor.capture())).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("sendMessageToChannelValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetTextBodyTypeByDefault() {
    // Given SendMessageInChat without bodyType
    sendMessageToChannel.setBodyType(null);
    // When
    sendMessageToChannel.invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = messageCaptor.getValue();
    assertThat(chatMessage.body.contentType).isEqualTo(BodyType.TEXT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"html", "HTML", "text", "TexT"})
  public void invoke_shouldSetTextBodyType(String input) {
    // Given
    sendMessageToChannel.setBodyType(input);
    // When
    sendMessageToChannel.invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = messageCaptor.getValue();
    assertThat(chatMessage.body.contentType).isEqualTo(BodyType.valueOf(input.toUpperCase()));
  }
}
