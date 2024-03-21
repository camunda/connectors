/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.Chat;
import com.microsoft.graph.models.ChatType;
import com.microsoft.graph.requests.ChatCollectionRequest;
import com.microsoft.graph.requests.ChatCollectionRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.serializer.AdditionalDataManager;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.Member;
import io.camunda.connector.model.request.data.CreateChat;
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
    Member member = new Member();
    member.setUserPrincipalName(ActualValue.Channel.OWNER);
    member.setRoles(List.of("owner"));
    CreateChat createChat = new CreateChat(ChatType.ONE_ON_ONE.name(), null, List.of(member));
    // when
    Object result = operationFactory.getService(createChat).invoke(graphClient);
    // then
    assertThat(result).isNotNull();
    AdditionalDataManager additionalDataManager =
        chatArgumentCaptor.getValue().members.getCurrentPage().getFirst().additionalDataManager();
    assertThat(additionalDataManager.get(Member.USER_DATA_TYPE))
        .isEqualTo(Member.USER_CONVERSATION_MEMBER);
    assertThat(additionalDataManager.get(Member.USER_DATA_BIND))
        .isEqualTo(member.getAsGraphJsonPrimitive());
  }

  @ParameterizedTest
  @MethodSource("createChatValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }
}
