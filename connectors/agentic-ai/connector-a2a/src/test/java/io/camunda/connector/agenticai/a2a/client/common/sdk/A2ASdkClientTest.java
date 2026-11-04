/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class A2ASdkClientTest {

  private final A2aDocumentToPartConverter documentToPartConverter =
      mock(A2aDocumentToPartConverter.class);
  private final A2aSdkObjectConverter objectConverter = mock(A2aSdkObjectConverter.class);

  @Test
  void shouldSendUserMessageWithTextOnly() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(
            sdkClient, mock(ManagedChannelFactory.class), documentToPartConverter, objectConverter);

    client.sendMessage(new A2aOutgoingMessage("Hello", null, null, null, null));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(sdkClient).sendMessage(captor.capture());
    Message message = captor.getValue();
    assertThat(message.role()).isEqualTo(Message.Role.ROLE_USER);
    assertThat(message.messageId()).isNotBlank();
    assertThat(message.parts()).containsExactly(new TextPart("Hello", null));
    assertThat(message.contextId()).isNull();
    assertThat(message.taskId()).isNull();
    assertThat(message.referenceTaskIds()).isNullOrEmpty();
  }

  @Test
  void shouldSendMessageWithIdsAndDocuments() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(
            sdkClient, mock(ManagedChannelFactory.class), documentToPartConverter, objectConverter);
    Document document = mock(Document.class);
    Part<?> documentPart = new TextPart("doc", null);
    doReturn(List.of(documentPart)).when(documentToPartConverter).convert(List.of(document));

    client.sendMessage(
        new A2aOutgoingMessage("Hello", List.of(document), "ctx-1", "task-1", List.of("ref-1")));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(sdkClient).sendMessage(captor.capture());
    Message message = captor.getValue();
    assertThat(message.parts()).containsExactly(new TextPart("Hello", null), documentPart);
    assertThat(message.contextId()).isEqualTo("ctx-1");
    assertThat(message.taskId()).isEqualTo("task-1");
    assertThat(message.referenceTaskIds()).containsExactly("ref-1");
  }

  @Test
  void shouldWrapSendMessageA2AClientExceptionInConnectorException() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(
            sdkClient, mock(ManagedChannelFactory.class), documentToPartConverter, objectConverter);
    A2AClientException expectedException = new A2AClientException("Test exception");

    doThrow(expectedException).when(sdkClient).sendMessage(any(Message.class));

    assertThatThrownBy(
            () -> client.sendMessage(new A2aOutgoingMessage("Hello", null, null, null, null)))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "A2A_CLIENT_SEND_MESSAGE_FAILED")
        .hasCause(expectedException);
  }

  @Test
  void shouldGetTaskSuccessfully() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(
            sdkClient, mock(ManagedChannelFactory.class), documentToPartConverter, objectConverter);

    final var request = new TaskQueryParams("task-123", 3);
    final var sdkTask = mock(Task.class);
    final var expectedTask =
        A2aTask.builder()
            .id("task-123")
            .contextId("ctx-1")
            .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.WORKING).build())
            .build();

    when(sdkClient.getTask(request)).thenReturn(sdkTask);
    when(objectConverter.convert(sdkTask)).thenReturn(expectedTask);

    assertThat(client.getTask("task-123", 3)).isEqualTo(expectedTask);
  }

  @Test
  void shouldWrapGetTaskA2AClientExceptionInConnectorException() throws A2AClientException {
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(
            sdkClient, mock(ManagedChannelFactory.class), documentToPartConverter, objectConverter);

    final var request = new TaskQueryParams("task-123", 3);
    A2AClientException expectedException = new A2AClientException("Test exception");

    doThrow(expectedException).when(sdkClient).getTask(request);

    assertThatThrownBy(() -> client.getTask("task-123", 3))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "A2A_CLIENT_TASK_RETRIEVAL_FAILED")
        .hasCause(expectedException);
  }

  @Test
  void shouldCloseClientAndChannelFactory() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aSdkClient client =
        new A2aSdkClient(sdkClient, channelFactory, documentToPartConverter, objectConverter);

    client.close();

    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleNullClientOnClose() {
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aSdkClient client =
        new A2aSdkClient(null, channelFactory, documentToPartConverter, objectConverter);

    client.close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleNullChannelFactoryOnClose() throws Exception {
    // given
    Client sdkClient = mock(Client.class);
    A2aSdkClient client =
        new A2aSdkClient(sdkClient, null, documentToPartConverter, objectConverter);

    client.close();
    verify(sdkClient).close();
  }

  @Test
  void shouldContinueClosingChannelFactoryEvenIfClientThrows() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aSdkClient client =
        new A2aSdkClient(sdkClient, channelFactory, documentToPartConverter, objectConverter);

    doThrow(new RuntimeException("Client close failed")).when(sdkClient).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleExceptionFromChannelFactoryClose() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aSdkClient client =
        new A2aSdkClient(sdkClient, channelFactory, documentToPartConverter, objectConverter);

    doThrow(new RuntimeException("ChannelFactory close failed")).when(channelFactory).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }

  @Test
  void shouldHandleExceptionsFromBothOnClose() throws Exception {
    Client sdkClient = mock(Client.class);
    ManagedChannelFactory channelFactory = mock(ManagedChannelFactory.class);
    A2aSdkClient client =
        new A2aSdkClient(sdkClient, channelFactory, documentToPartConverter, objectConverter);

    doThrow(new RuntimeException("Client close failed")).when(sdkClient).close();
    doThrow(new RuntimeException("ChannelFactory close failed")).when(channelFactory).close();

    client.close();
    verify(sdkClient).close();
    verify(channelFactory).close();
  }
}
