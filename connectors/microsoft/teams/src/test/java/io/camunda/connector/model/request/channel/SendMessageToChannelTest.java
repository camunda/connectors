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
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.teams.TeamsRequestBuilder;
import com.microsoft.graph.teams.item.TeamItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.ChannelsRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.ChannelItemRequestBuilder;
import com.microsoft.graph.teams.item.channels.item.messages.MessagesRequestBuilder;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.Attachment;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import java.util.List;
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
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            null,
            "channel content",
            null,
            null);

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
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            input,
            "channel content",
            null,
            null);
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("channel content");
    assertThat(chatMessage.getBody().getContentType().toString().toLowerCase())
        .isEqualTo(input.toLowerCase());
  }

  @Test
  public void invoke_shouldSetAttachmentsAndAutoAppendTags() {
    // Given
    Attachment card =
        new Attachment(
            "adaptiveCardId",
            "application/vnd.microsoft.card.adaptive",
            "{\"type\":\"AdaptiveCard\",\"version\":\"1.6\",\"body\":[]}");
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            "HTML",
            "Hello",
            null,
            List.of(card));
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    List<ChatMessageAttachment> attachments = chatMessage.getAttachments();
    assertThat(attachments).hasSize(1);
    assertThat(attachments.getFirst().getId()).isEqualTo("adaptiveCardId");
    assertThat(attachments.getFirst().getContentType())
        .isEqualTo("application/vnd.microsoft.card.adaptive");
    assertThat(attachments.getFirst().getContent())
        .isEqualTo("{\"type\":\"AdaptiveCard\",\"version\":\"1.6\",\"body\":[]}");
    assertThat(chatMessage.getBody().getContent())
        .isEqualTo("Hello<attachment id=\"adaptiveCardId\"></attachment>");
  }

  @Test
  public void invoke_shouldNotAppendTagWhenAlreadyPresent() {
    // Given
    Attachment card =
        new Attachment("myCard", "application/vnd.microsoft.card.thumbnail", "{\"title\":\"Hi\"}");
    String content = "Message<attachment id=\"myCard\"></attachment>";
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            "HTML",
            content,
            null,
            List.of(card));
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getBody().getContent()).isEqualTo(content);
    assertThat(chatMessage.getAttachments()).hasSize(1);
  }

  @Test
  public void invoke_shouldHandleMultipleAttachments() {
    // Given
    Attachment card1 =
        new Attachment(
            "card1", "application/vnd.microsoft.card.adaptive", "{\"type\":\"AdaptiveCard\"}");
    Attachment card2 =
        new Attachment("card2", "application/vnd.microsoft.card.thumbnail", "{\"title\":\"Test\"}");
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            "HTML",
            "Cards",
            null,
            List.of(card1, card2));
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getAttachments()).hasSize(2);
    assertThat(chatMessage.getBody().getContent())
        .isEqualTo(
            "Cards<attachment id=\"card1\"></attachment><attachment id=\"card2\"></attachment>");
  }

  @Test
  public void invoke_shouldNotSetAttachmentsWhenNull() {
    // Given
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            "HTML",
            "Hello",
            null,
            null);
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getAttachments()).isNull();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("Hello");
  }

  @Test
  public void invoke_shouldNotSetAttachmentsWhenEmptyList() {
    // Given
    sendMessageToChannel =
        new SendMessageToChannel(
            ActualValue.Channel.GROUP_ID,
            ActualValue.Channel.CHANNEL_ID,
            "HTML",
            "Hello",
            null,
            List.of());
    // When
    operationFactory.getService(sendMessageToChannel).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageArgumentCaptor.getValue();
    assertThat(chatMessage.getAttachments()).isNull();
    assertThat(chatMessage.getBody().getContent()).isEqualTo("Hello");
  }
}
