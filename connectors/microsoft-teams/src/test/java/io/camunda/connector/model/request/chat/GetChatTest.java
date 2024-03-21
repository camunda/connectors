/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.requests.ChatRequest;
import com.microsoft.graph.requests.ChatRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.request.data.GetChat;
import io.camunda.connector.suppliers.ObjectMapperSupplier;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetChatTest extends BaseTest {

  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private ChatRequestBuilder chatRequestBuilder;
  @Mock private ChatRequest chatRequest;

  @ParameterizedTest
  @MethodSource("getChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetOptionalPropertiesIfTheyExist() {
    // Given
    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.buildRequest()).thenReturn(chatRequest);

    when(chatRequest.get()).thenReturn(new Chat());

    GetChat getChat = new GetChat(ActualValue.Chat.CHAT_ID, "members");
    // When
    Object result = operationFactory.getService(getChat).invoke(graphServiceClient);
    // Then
    verify(chatRequest).expand("members");
    assertThat(result).isNotNull();
  }

  @Test
  public void invoke_shouldReturnChatWithOutNullFieldsInResponse() throws JsonProcessingException {
    // Given
    String chatStringResponse =
        "{\"oDataType\":null,\"id\":\"19:e37f90808e7748d7bbbb2029ed17f643@thread.v2\",\"chatType\":\"GROUP\",\"members\":null,\"messages\":null}";

    ObjectMapper objectMapper = ObjectMapperSupplier.objectMapper();
    Chat chat = objectMapper.readValue(chatStringResponse, Chat.class);

    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.buildRequest()).thenReturn(chatRequest);

    when(chatRequest.get()).thenReturn(chat);
    GetChat getChat = new GetChat(ActualValue.Chat.CHAT_ID, null);
    // When
    Object result = operationFactory.getService(getChat).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    assertThat(objectMapper.writer().writeValueAsString(result))
        .isEqualTo(
            "{\"id\":\"19:e37f90808e7748d7bbbb2029ed17f643@thread.v2\",\"chatType\":\"GROUP\"}");
  }
}
