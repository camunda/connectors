/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatType;
import com.microsoft.graph.requests.ChatCollectionRequest;
import com.microsoft.graph.requests.ChatCollectionRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.serializer.AdditionalDataManager;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.model.Member;
import java.util.List;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateChatTest extends BaseTest {
  @Mock private GraphServiceClient<Request> graphClient;
  @Mock private ChatCollectionRequestBuilder chatCollectionRequestBuilder;
  @Mock private ChatCollectionRequest chatCollectionRequest;
  @Captor private ArgumentCaptor<Chat> chatArgumentCaptor;

  @Test
  public void invoke_shouldReturnChatAndSetMembers() {
    // Given
    when(graphClient.chats()).thenReturn(chatCollectionRequestBuilder);
    when(chatCollectionRequestBuilder.buildRequest()).thenReturn(chatCollectionRequest);
    when(chatCollectionRequest.post(chatArgumentCaptor.capture())).thenReturn(new Chat());
    CreateChat createChat = new CreateChat();
    createChat.setChatType(ChatType.ONE_ON_ONE.name());
    Member member = new Member();
    member.setUserPrincipalName(ActualValue.Channel.OWNER);
    member.setRoles(List.of("owner"));
    createChat.setMembers(List.of(member));
    // when
    Object invoke = createChat.invoke(graphClient);
    // then
    assertThat(invoke).isNotNull();
    AdditionalDataManager additionalDataManager =
        chatArgumentCaptor.getValue().members.getCurrentPage().get(0).additionalDataManager();
    assertThat(additionalDataManager.get(Member.USER_DATA_TYPE))
        .isEqualTo(Member.USER_CONVERSATION_MEMBER);
    assertThat(additionalDataManager.get(Member.USER_DATA_BIND))
        .isEqualTo(member.getAsGraphJsonPrimitive());
  }

  @ParameterizedTest
  @MethodSource("createChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input)
      throws JsonProcessingException {
    // Given request without one required field
    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(CreateChat.class);
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request.getData()),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
