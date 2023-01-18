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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.graph.requests.ChannelCollectionReferenceRequest;
import com.microsoft.graph.requests.ChannelCollectionResponse;
import com.microsoft.graph.requests.ChannelCollectionWithReferencesPage;
import com.microsoft.graph.requests.ChannelCollectionWithReferencesRequestBuilder;
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
class ListChannelsTest extends BaseTest {

  @Mock private GraphServiceClient<Request> graphServiceClient;
  @Mock private TeamRequestBuilder teamRequestBuilder;

  @Mock
  private ChannelCollectionWithReferencesRequestBuilder
      channelCollectionWithReferencesRequestBuilder;

  @Mock private ChannelCollectionReferenceRequest channelCollectionReferenceRequest;

  @ParameterizedTest
  @MethodSource("listChannelsValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input)
      throws JsonProcessingException {
    // Given request without one required field
    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(ListChannels.class);
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
    when(teamRequestBuilder.allChannels())
        .thenReturn(channelCollectionWithReferencesRequestBuilder);
    when(channelCollectionWithReferencesRequestBuilder.buildRequest())
        .thenReturn(channelCollectionReferenceRequest);
    when(channelCollectionReferenceRequest.filter(ActualValue.Channel.FILTER))
        .thenReturn(channelCollectionReferenceRequest);
    when(channelCollectionReferenceRequest.get())
        .thenReturn(new ChannelCollectionWithReferencesPage(new ChannelCollectionResponse(), null));

    ListChannels listChannels = new ListChannels();
    listChannels.setGroupId(ActualValue.Channel.GROUP_ID);
    listChannels.setFilter(ActualValue.Channel.FILTER);
    // When
    Object invoke = listChannels.invoke(graphServiceClient);
    // Then
    verify(channelCollectionReferenceRequest).filter(ActualValue.Channel.FILTER);
    assertThat(invoke).isNotNull();
  }
}
