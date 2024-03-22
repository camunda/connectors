/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.request.data.GetChat;
import io.camunda.connector.suppliers.ObjectMapperSupplier;
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
class GetChatTest extends BaseTest {

  private GraphServiceClient graphServiceClient;
  @Mock private RequestAdapter requestAdapter;
  @Captor private ArgumentCaptor<RequestInformation> requestInformationArgumentCaptor;

  @BeforeEach
  public void init() {
    graphServiceClient = new GraphServiceClient(requestAdapter);
  }

  @ParameterizedTest
  @MethodSource("getChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetOptionalPropertiesIfTheyExist() {
    // Given

    when(requestAdapter.send(requestInformationArgumentCaptor.capture(), any(), any()))
        .thenReturn(new Chat());

    GetChat getChat = new GetChat(ActualValue.Chat.CHAT_ID, "members");
    // When
    Object result = operationFactory.getService(getChat).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    RequestInformation value = requestInformationArgumentCaptor.getValue();
    assertThat(value.pathParameters.get("chat%2Did")).isEqualTo(ActualValue.Chat.CHAT_ID);
    assertThat(value.getQueryParameters().get("%24expand")).isEqualTo(List.of("members"));
  }

  @Test
  public void invoke_shouldReturnChatWithOutNullFieldsInResponse() throws JsonProcessingException {
    // Given
    String chatStringResponse =
        "{\"oDataType\":null,\"id\":\"19:e37f90808e7748d7bbbb2029ed17f643@thread.v2\",\"chatType\":\"GROUP\",\"members\":null,\"messages\":null}";

    ObjectMapper objectMapper = ObjectMapperSupplier.objectMapper();
    Chat chat = objectMapper.readValue(chatStringResponse, Chat.class);

    when(requestAdapter.send(requestInformationArgumentCaptor.capture(), any(), any()))
        .thenReturn(chat);
    GetChat getChat = new GetChat(ActualValue.Chat.CHAT_ID, null);
    // When
    Object result = operationFactory.getService(getChat).invoke(graphServiceClient);
    // Then
    assertThat(result).isNotNull();
    JsonNode jsonNode = objectMapper.convertValue(result, JsonNode.class);
    assertThat(jsonNode.get("oDataType")).isNull();
    assertThat(jsonNode.get("members")).isNull();
    assertThat(jsonNode.get("messages")).isNull();
    assertThat(jsonNode.get("id").asText())
        .isEqualTo("19:e37f90808e7748d7bbbb2029ed17f643@thread.v2");
    assertThat(jsonNode.get("chatType").asText()).isEqualTo("Group");
  }
}
