/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.microsoft.graph.chats.ChatsRequestBuilder;
import com.microsoft.graph.chats.item.ChatItemRequestBuilder;
import com.microsoft.graph.chats.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.request.data.SendMessageInChat;
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
class SendMessageInChatTest extends BaseTest {

  private SendMessageInChat sendMessageInChat;
  @Mock private GraphServiceClient graphServiceClient;
  @Mock private ChatsRequestBuilder chatsRequestBuilder;
  @Mock private ChatItemRequestBuilder chatItemRequestBuilder;
  @Mock private MessagesRequestBuilder messagesRequestBuilder;

  @Captor private ArgumentCaptor<ChatMessage> chatMessageCaptor;

  @BeforeEach
  public void init() {
    sendMessageInChat = new SendMessageInChat(ActualValue.Chat.CHAT_ID, "content", null);
    when(graphServiceClient.chats()).thenReturn(chatsRequestBuilder);
    when(chatsRequestBuilder.byChatId(ActualValue.Chat.CHAT_ID)).thenReturn(chatItemRequestBuilder);
    when(chatItemRequestBuilder.messages()).thenReturn(messagesRequestBuilder);
    when(messagesRequestBuilder.post(chatMessageCaptor.capture())).thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("sendMessageInChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetTextBodyTypeByDefault() {
    // Given SendMessageInChat without bodyType
    // When
    operationFactory.getService(sendMessageInChat).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageCaptor.getValue();
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Text);
    assertThat(chatMessage.getBody().getContent()).isEqualTo("content");
  }

  @Test
  public void invoke_shouldSetTextBodyTypeContentIsNotEscaped() {
    // Given SendMessageInChat without bodyType
    // When
    sendMessageInChat = new SendMessageInChat(ActualValue.Chat.CHAT_ID, "\"normal\" content", null);
    operationFactory.getService(sendMessageInChat).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageCaptor.getValue();
    assertThat(chatMessage.getBody().getContentType()).isEqualTo(BodyType.Text);
    assertThat(chatMessage.getBody().getContent()).isEqualTo("\"normal\" content");
  }

  @ParameterizedTest
  @ValueSource(strings = {"html", "HTML", "text", "TexT"})
  public void invoke_shouldSetTextBodyType(String input) {
    // Given
    sendMessageInChat = new SendMessageInChat(ActualValue.Chat.CHAT_ID, "content", input);
    // When
    operationFactory.getService(sendMessageInChat).invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageCaptor.getValue();
    assertThat(chatMessage.getBody().getContentType().value.toLowerCase())
        .isEqualTo(input.toLowerCase());
    assertThat(chatMessage.getBody().getContent()).isEqualTo("content");
  }
}
