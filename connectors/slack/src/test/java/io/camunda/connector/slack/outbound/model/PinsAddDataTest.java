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
import com.slack.api.methods.request.pins.PinsAddRequest;
import com.slack.api.methods.response.pins.PinsAddResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PinsAddDataTest {

  @Mock private MethodsClient methodsClient;
  @Mock private PinsAddResponse pinsAddResponse;

  @Test
  void invoke_shouldCallSlackClientWithCorrectData() throws Exception {
    // Given
    PinsAddData data = new PinsAddData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsAdd((PinsAddRequest) Mockito.any())).thenReturn(pinsAddResponse);
    when(pinsAddResponse.isOk()).thenReturn(true);

    // When
    data.invoke(methodsClient);

    // Then
    ArgumentCaptor<PinsAddRequest> captor = ArgumentCaptor.forClass(PinsAddRequest.class);
    Mockito.verify(methodsClient).pinsAdd(captor.capture());

    PinsAddRequest request = captor.getValue();
    assertThat(request.getChannel()).isEqualTo("C123ABC456");
    assertThat(request.getTimestamp()).isEqualTo("1503435956.000247");
  }

  @Test
  void invoke_shouldThrowExceptionWhenSlackFails() throws Exception {
    // Given
    PinsAddData data = new PinsAddData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsAdd((PinsAddRequest) Mockito.any())).thenReturn(pinsAddResponse);
    when(pinsAddResponse.isOk()).thenReturn(false);
    when(pinsAddResponse.getError()).thenReturn("channel_not_found");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to pin message: channel_not_found");
  }

  @Test
  void invoke_shouldThrowExceptionWhenMessageNotFound() throws Exception {
    // Given
    PinsAddData data = new PinsAddData("C123ABC456", "1503435956.000247");

    when(methodsClient.pinsAdd((PinsAddRequest) Mockito.any())).thenReturn(pinsAddResponse);
    when(pinsAddResponse.isOk()).thenReturn(false);
    when(pinsAddResponse.getError()).thenReturn("message_not_found");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to pin message: message_not_found");
  }
}
