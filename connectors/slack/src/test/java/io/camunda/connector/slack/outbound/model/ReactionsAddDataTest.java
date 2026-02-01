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
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.response.reactions.ReactionsAddResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactionsAddDataTest {

  @Mock private MethodsClient methodsClient;
  @Mock private ReactionsAddResponse reactionsAddResponse;

  @Test
  void invoke_shouldCallSlackClientWithCorrectData() throws Exception {
    // Given
    ReactionsAddData data = new ReactionsAddData("C123ABC456", "eyes", "1503435956.000247");

    when(methodsClient.reactionsAdd((ReactionsAddRequest) Mockito.any()))
        .thenReturn(reactionsAddResponse);
    when(reactionsAddResponse.isOk()).thenReturn(true);

    // When
    data.invoke(methodsClient);

    // Then
    ArgumentCaptor<ReactionsAddRequest> captor = ArgumentCaptor.forClass(ReactionsAddRequest.class);
    Mockito.verify(methodsClient).reactionsAdd(captor.capture());

    ReactionsAddRequest request = captor.getValue();
    assertThat(request.getChannel()).isEqualTo("C123ABC456");
    assertThat(request.getName()).isEqualTo("eyes");
    assertThat(request.getTimestamp()).isEqualTo("1503435956.000247");
  }

  @Test
  void invoke_shouldThrowExceptionWhenSlackFails() throws Exception {
    // Given
    ReactionsAddData data = new ReactionsAddData("C123ABC456", "eyes", "1503435956.000247");

    when(methodsClient.reactionsAdd((ReactionsAddRequest) Mockito.any()))
        .thenReturn(reactionsAddResponse);
    when(reactionsAddResponse.isOk()).thenReturn(false);
    when(reactionsAddResponse.getError()).thenReturn("channel_not_found");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to add reaction: channel_not_found");
  }

  @Test
  void invoke_shouldThrowExceptionWhenReactionAlreadyAdded() throws Exception {
    // Given
    ReactionsAddData data = new ReactionsAddData("C123ABC456", "eyes", "1503435956.000247");

    when(methodsClient.reactionsAdd((ReactionsAddRequest) Mockito.any()))
        .thenReturn(reactionsAddResponse);
    when(reactionsAddResponse.isOk()).thenReturn(false);
    when(reactionsAddResponse.getError()).thenReturn("already_reacted");

    // When
    Throwable thrown = catchThrowable(() -> data.invoke(methodsClient));

    // Then
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to add reaction: already_reacted");
  }
}
