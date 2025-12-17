/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.ChatMessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.model.OrderBy;
import io.camunda.connector.model.request.data.ListMessagesInChat;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListMessagesInChatTest extends BaseTest {
  private GraphServiceClient graphServiceClient;
  @Mock private RequestAdapter requestAdapter;
  @Captor private ArgumentCaptor<RequestInformation> requestInformationArgumentCaptor;

  @BeforeEach
  public void init() {
    graphServiceClient = new GraphServiceClient(requestAdapter);
  }

  @Test
  public void invoke_shouldSetOptionalProperties() {
    // Given
    when(requestAdapter.send(requestInformationArgumentCaptor.capture(), any(), any()))
        .thenReturn(new ChatMessageCollectionResponse());

    ListMessagesInChat listMessagesInChat =
        new ListMessagesInChat(
            ActualValue.Chat.CHAT_ID,
            ActualValue.Channel.TOP,
            OrderBy.createdDateTime,
            ActualValue.Chat.FILTER);
    // When
    Object result = operationFactory.getService(listMessagesInChat).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    RequestInformation value = requestInformationArgumentCaptor.getValue();
    assertThat(value.pathParameters.get("chat%2Did")).isEqualTo(ActualValue.Chat.CHAT_ID);
    assertThat(value.getQueryParameters().get("%24top"))
        .isEqualTo(Integer.valueOf(ActualValue.Channel.TOP));
    assertThat(value.getQueryParameters().get("%24filter")).isEqualTo(ActualValue.Chat.FILTER);
    assertThat(((List<String>) value.getQueryParameters().get("%24orderby")).getFirst())
        .isEqualTo(OrderBy.createdDateTime.getValue());
  }

  @ParameterizedTest
  @MethodSource("listMessagesInChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(MSTeamsRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
