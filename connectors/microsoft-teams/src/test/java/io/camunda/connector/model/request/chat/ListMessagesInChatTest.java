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

import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.model.OrderBy;
import java.util.ArrayList;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListMessagesInChatTest extends BaseTest {
  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private ChatRequestBuilder chatRequestBuilder;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;

  @Test
  public void invoke_shouldSetOptionalProperties() {
    // Given
    when(graphServiceClient.chats(ActualValue.Chat.CHAT_ID)).thenReturn(chatRequestBuilder);
    when(chatRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    when(chatMessageCollectionRequest.get())
        .thenReturn(new ChatMessageCollectionPage(new ArrayList<>(), null));

    ListMessagesInChat listMessagesInChat = new ListMessagesInChat();
    listMessagesInChat.setChatId(ActualValue.Chat.CHAT_ID);
    listMessagesInChat.setFilter(ActualValue.Chat.FILTER);
    listMessagesInChat.setOrderBy(OrderBy.createdDateTime);
    listMessagesInChat.setTop(ActualValue.Channel.TOP);
    // When
    Object invoke = listMessagesInChat.invoke(graphServiceClient);
    // Then
    assertThat(invoke).isNotNull();
    verify(chatMessageCollectionRequest).top(Integer.parseInt(ActualValue.Channel.TOP));
    verify(chatMessageCollectionRequest).filter(ActualValue.Chat.FILTER);
    verify(chatMessageCollectionRequest).orderBy(OrderBy.createdDateTime.getValue());
  }

  @ParameterizedTest
  @MethodSource("listMessagesInChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    // Given request without one required field
    MSTeamsRequest request = gson.fromJson(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(ListMessagesInChat.class);
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request.getData()),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
