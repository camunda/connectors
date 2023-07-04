/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendMessageInChatTest extends BaseTest {

  private SendMessageInChat sendMessageInChat;
  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private ChatRequestBuilder chatRequestBuilder;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;
  @Captor private ArgumentCaptor<ChatMessage> chatMessageCaptor;

  @BeforeEach
  public void init() {
    sendMessageInChat = new SendMessageInChat();
    sendMessageInChat.setChatId(ActualValue.Chat.CHAT_ID);
    sendMessageInChat.setContent("content");

    Mockito.when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    Mockito.when(chatRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    Mockito.when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    Mockito.when(chatMessageCollectionRequest.post(chatMessageCaptor.capture()))
        .thenReturn(new ChatMessage());
  }

  @ParameterizedTest
  @MethodSource("sendMessageInChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetTextBodyTypeByDefault() {
    // Given SendMessageInChat without bodyType
    sendMessageInChat.setBodyType(null);
    // When
    sendMessageInChat.invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageCaptor.getValue();
    assertThat(chatMessage.body.contentType).isEqualTo(BodyType.TEXT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"html", "HTML", "text", "TexT"})
  public void invoke_shouldSetTextBodyType(String input) {
    // Given
    sendMessageInChat.setBodyType(input);
    // When
    sendMessageInChat.invoke(graphServiceClient);
    // Then
    ChatMessage chatMessage = chatMessageCaptor.getValue();
    assertThat(chatMessage.body.contentType).isEqualTo(BodyType.valueOf(input.toUpperCase()));
  }
}
