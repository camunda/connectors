/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.microsoft.graph.models.ChannelCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.camunda.connector.BaseTest;
import io.camunda.connector.model.request.data.ListChannels;
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
class ListChannelsTest extends BaseTest {
  private GraphServiceClient graphServiceClient;
  @Mock private RequestAdapter requestAdapter;
  @Captor private ArgumentCaptor<RequestInformation> requestInformationArgumentCaptor;

  @BeforeEach
  public void init() {
    graphServiceClient = new GraphServiceClient(requestAdapter);
  }

  @ParameterizedTest
  @MethodSource("listChannelsValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }

  @Test
  public void invoke_shouldSetOptionalPropertiesIfTheyExist() {
    // Given
    when(requestAdapter.send(requestInformationArgumentCaptor.capture(), any(), any()))
        .thenReturn(new ChannelCollectionResponse());
    ListChannels listChannels =
        new ListChannels(ActualValue.Channel.GROUP_ID, ActualValue.Channel.FILTER);
    // When
    Object result = operationFactory.getService(listChannels).invoke(graphServiceClient);
    // Then
    RequestInformation value = requestInformationArgumentCaptor.getValue();
    assertThat(value.getQueryParameters().get("%24filter")).isEqualTo(ActualValue.Channel.FILTER);
    assertThat(value.pathParameters.get("team%2Did")).isEqualTo(ActualValue.Channel.GROUP_ID);
    assertThat(result).isNotNull();
  }
}
