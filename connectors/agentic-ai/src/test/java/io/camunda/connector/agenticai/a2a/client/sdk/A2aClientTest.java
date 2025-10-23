/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.client.Client;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.camunda.connector.agenticai.a2a.client.sdk.grpc.ManagedChannelFactory;
import org.junit.jupiter.api.Test;

class A2aClientTest {

  @Test
  void shouldSendMessageSuccessfully() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);
    Message message = mock(Message.class);

    client.sendMessage(message);

    verify(sdkClient).sendMessage(message);
  }

  @Test
  void shouldWrapSendMessageA2AClientExceptionInRuntimeException() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);
    Message message = mock(Message.class);
    A2AClientException expectedException = new A2AClientException("Test exception");

    doThrow(expectedException).when(sdkClient).sendMessage(message);

    assertThatThrownBy(() -> client.sendMessage(message))
        .isInstanceOf(RuntimeException.class)
        .hasCause(expectedException);
  }

  @Test
  void shouldGetTaskSuccessfully() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    final var request = new TaskQueryParams("task-123");
    final var expectedTask = mock(Task.class);

    when(sdkClient.getTask(request)).thenReturn(expectedTask);

    var actualTask = client.getTask(request);
    assertThat(actualTask).isEqualTo(expectedTask);
  }

  @Test
  void shouldWrapGetTaskA2AClientExceptionInRuntimeException() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    final var request = new TaskQueryParams("task-123");
    A2AClientException expectedException = new A2AClientException("Test exception");

    doThrow(expectedException).when(sdkClient).getTask(request);

    assertThatThrownBy(() -> client.getTask(request))
        .isInstanceOf(RuntimeException.class)
        .hasCause(expectedException);
  }

  @Test
  void shouldCloseClientAndChannelFactory() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    client.close();

    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleNullClientOnClose() {
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(null, channelFactory);

    client.close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleNullChannelFactoryOnClose() throws Exception {
    // given
    Client sdkClient = mock(Client.class);
    A2aClient client = new A2aClient(sdkClient, null);

    client.close();
    verify(sdkClient).close();
  }

  @Test
  void shouldContinueClosingChannelFactoryEvenIfClientThrows() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    doThrow(new RuntimeException("Client close failed")).when(sdkClient).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleExceptionFromChannelFactoryClose() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    doThrow(new RuntimeException("ChannelFactory close failed")).when(channelFactory).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleExceptionsFromBothOnClose() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aClient client = new A2aClient(sdkClient, channelFactory);

    doThrow(new RuntimeException("Client close failed")).when(sdkClient).close();
    doThrow(new RuntimeException("ChannelFactory close failed")).when(channelFactory).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }
}
