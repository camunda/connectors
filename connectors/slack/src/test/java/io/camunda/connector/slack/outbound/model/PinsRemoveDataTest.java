/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.Mockito.when;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.pins.PinsRemoveRequest;
import com.slack.api.methods.response.pins.PinsRemoveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PinsRemoveDataTest {

  @Mock private MethodsClient methodsClient;
  @Mock private PinsRemoveResponse pinsRemoveResponse;

  @Test
  void invoke_shouldCallSlackClientWithCorrectData() throws Exception {
    // Given
    PinsRemoveData data = new PinsRemoveData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsRemove((PinsRemoveRequest) Mockito.any()))
        .thenReturn(pinsRemoveResponse);
    when(pinsRemoveResponse.isOk()).thenReturn(true);

    // When
    data.invoke(methodsClient);

    // Then
    ArgumentCaptor<PinsRemoveRequest> captor = ArgumentCaptor.forClass(PinsRemoveRequest.class);
    Mockito.verify(methodsClient).pinsRemove(captor.capture());

    PinsRemoveRequest request = captor.getValue();
    assertThat(request.getChannel()).isEqualTo("C123ABC456");
    assertThat(request.getTimestamp()).isEqualTo("1503435956.000247");
  }

  @Test
  void invoke_shouldThrowExceptionWhenSlackFails() throws Exception {
    // Given
    PinsRemoveData data = new PinsRemoveData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsRemove((PinsRemoveRequest) Mockito.any()))
        .thenReturn(pinsRemoveResponse);
    when(pinsRemoveResponse.isOk()).thenReturn(false);
    when(pinsRemoveResponse.getError()).thenReturn("no_pin");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to unpin message: no_pin");
  }

  @Test
  void invoke_shouldThrowExceptionWhenChannelNotFound() throws Exception {
    // Given
    PinsRemoveData data = new PinsRemoveData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsRemove((PinsRemoveRequest) Mockito.any()))
        .thenReturn(pinsRemoveResponse);
    when(pinsRemoveResponse.isOk()).thenReturn(false);
    when(pinsRemoveResponse.getError()).thenReturn("channel_not_found");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to unpin message: channel_not_found");
  }
}
