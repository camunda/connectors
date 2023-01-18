/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.requests.ChatRequest;
import com.microsoft.graph.requests.ChatRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
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
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input)
      throws JsonProcessingException {
    // Given request without one required field
    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(GetChat.class);
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request.getData()),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }

  @Test
  public void invoke_shouldSetOptionalPropertiesIfTheyExist() {
    // Given
    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.buildRequest()).thenReturn(chatRequest);

    when(chatRequest.get()).thenReturn(new Chat());

    GetChat getChat = new GetChat();
    getChat.setChatId(ActualValue.Chat.CHAT_ID);
    getChat.setExpand("members");
    // When
    Object invoke = getChat.invoke(graphServiceClient);
    // Then
    verify(chatRequest).expand("members");
    assertThat(invoke).isNotNull();
  }

  @Test
  public void invoke_shouldReturnChatWithOutNullFieldsInResponse() throws JsonProcessingException {
    // Given
    String chatStringResponse =
        "{\"oDataType\":null,\"id\":\"19:e37f90808e7748d7bbbb2029ed17f643@thread.v2\",\"chatType\":\"GROUP\",\"members\":null,\"messages\":null}";

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    Chat chat = objectMapper.readValue(chatStringResponse, Chat.class);

    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.buildRequest()).thenReturn(chatRequest);

    when(chatRequest.get()).thenReturn(chat);
    GetChat getChat = new GetChat();
    getChat.setChatId(ActualValue.Chat.CHAT_ID);
    // When
    Object invoke = getChat.invoke(graphServiceClient);
    // Then
    assertThat(invoke).isNotNull();
    assertThat(objectMapper.writer().writeValueAsString(invoke))
        .isEqualTo(
            "{\"id\":\"19:e37f90808e7748d7bbbb2029ed17f643@thread.v2\",\"chatType\":\"GROUP\"}");
  }
}
