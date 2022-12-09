/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.graph.requests.ChannelRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.ChatMessageCollectionRequestBuilder;
import com.microsoft.graph.requests.ChatMessageCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.TeamRequestBuilder;
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
class ListChannelMessagesTest extends BaseTest {

  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private TeamRequestBuilder teamRequestBuilder;
  @Mock private ChannelRequestBuilder channelRequestBuilder;
  @Mock private ChatMessageCollectionRequestBuilder chatMessageCollectionRequestBuilder;
  @Mock private ChatMessageCollectionRequest chatMessageCollectionRequest;

  @ParameterizedTest
  @MethodSource("listChannelMessagesValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    // Given request without one required field
    MSTeamsRequest request = gson.fromJson(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(ListChannelMessages.class);
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
    when(graphServiceClient.teams(ActualValue.Channel.GROUP_ID)).thenReturn(teamRequestBuilder);
    when(teamRequestBuilder.channels(ActualValue.Channel.CHANNEL_ID))
        .thenReturn(channelRequestBuilder);
    when(channelRequestBuilder.messages()).thenReturn(chatMessageCollectionRequestBuilder);
    when(chatMessageCollectionRequestBuilder.buildRequest())
        .thenReturn(chatMessageCollectionRequest);
    when(chatMessageCollectionRequest.get())
        .thenReturn(new ChatMessageCollectionPage(new ChatMessageCollectionResponse(), null));

    ListChannelMessages listChannelMessages = new ListChannelMessages();
    listChannelMessages.setGroupId(ActualValue.Channel.GROUP_ID);
    listChannelMessages.setChannelId(ActualValue.Channel.CHANNEL_ID);
    listChannelMessages.setIsExpand(Boolean.TRUE.toString());
    listChannelMessages.setTop(ActualValue.Channel.TOP);
    // When
    ChatMessageCollectionPage invoke = listChannelMessages.invoke(graphServiceClient);
    // Then
    verify(chatMessageCollectionRequest).top(Integer.parseInt(ActualValue.Channel.TOP));
    verify(chatMessageCollectionRequest).expand("replies");
    assertThat(invoke).isNotNull();
  }
}
