/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.api.TaskPoller;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.SendMessageOperationConfiguration.Parameters;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult.A2aMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult.A2aTaskResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus.TaskState;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aMessageSenderTest {

  private static final String MESSAGE_ID = "message-1";

  @Mock private A2aDocumentToPartConverter documentToPartConverter;
  @Mock private A2aSendMessageResponseHandler sendMessageResponseHandler;
  @Mock private TaskPoller taskPoller;
  @Mock private A2aSdkClientFactory clientFactory;
  @Mock private Client client;
  @Mock private AgentCard agentCard;
  @InjectMocks private A2aMessageSenderImpl messageSender;

  private final AtomicReference<BiConsumer<ClientEvent, AgentCard>> consumerRef =
      new AtomicReference<>();

  @BeforeEach
  void setUp() {
    when(clientFactory.buildClient(eq(agentCard), any()))
        .thenAnswer(
            inv -> {
              consumerRef.set(inv.getArgument(1));
              return client;
            });
  }

  @Test
  void shouldReturnImmediateResultWhenCompletedWithoutPolling() throws Exception {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);

    mockClientSendMessage(clientEvent);

    var actualResult = messageSender.sendMessage(agentCard, operation);

    assertThat(actualResult).isSameAs(expectedResult);
    verify(taskPoller, never()).poll(anyString(), any(), any(), any());
    verify(client).close();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskState.class,
      names = {"SUBMITTED", "WORKING"})
  void shouldPollWhenResultSubmittedOrWorking(TaskState taskState) throws Exception {
    var taskId = "task-1";
    var operation = newSendMessageOperation(Duration.ofSeconds(1));
    var task =
        new Task.Builder()
            .contextId("ctx-123")
            .id(taskId)
            .status(new TaskStatus(io.a2a.spec.TaskState.valueOf(taskState.name())))
            .build();
    var clientEvent = new TaskEvent(task);
    var submittedResult = taskResult(taskId, taskState);
    var expectedFinalResult = messageResult(MESSAGE_ID);

    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(submittedResult);
    when(taskPoller.poll(eq(taskId), eq(client), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(expectedFinalResult));

    mockClientSendMessage(clientEvent);

    var actualResult = messageSender.sendMessage(agentCard, operation);

    assertThat(actualResult).isSameAs(expectedFinalResult);

    // capture poll interval to assert equals default (500ms)
    ArgumentCaptor<Duration> pollIntervalCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(taskPoller)
        .poll(eq(taskId), eq(client), pollIntervalCaptor.capture(), any(Duration.class));
    assertThat(pollIntervalCaptor.getValue()).isEqualTo(Duration.ofMillis(500));
    verify(client).close();
  }

  @Test
  void shouldTimeoutWaitingForInitialResponse() throws Exception {
    // very short timeout to keep test fast
    var operation = newSendMessageOperation(Duration.ofMillis(10));

    // Do not trigger consumer -> future never completes
    doAnswer(inv -> null).when(client).sendMessage(any());

    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Timed out waiting for response from agent");

    verify(client).close();
  }

  @Test
  void shouldWrapExceptionFromHandler() throws Exception {
    var operation = newSendMessageOperation(Duration.ofSeconds(1));

    MessageEvent clientEvent = newMessageEvent();
    when(sendMessageResponseHandler.handleClientEvent(clientEvent))
        .thenThrow(new IllegalStateException("boom"));

    mockClientSendMessage(clientEvent);

    assertThatThrownBy(() -> messageSender.sendMessage(agentCard, operation))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("boom");

    verify(client).close();
  }

  @Test
  void shouldConvertDocumentsAndIncludePartsInMessage() throws Exception {
    Document document = mock(Document.class);
    var operation =
        new SendMessageOperationConfiguration(
            new Parameters("hello", List.of(document)), Duration.ofSeconds(1));

    MessageEvent clientEvent = newMessageEvent();
    var expectedResult = messageResult(MESSAGE_ID);
    when(sendMessageResponseHandler.handleClientEvent(clientEvent)).thenReturn(expectedResult);

    // convert document -> part
    var partFromDocument = new TextPart("document-part");
    when(documentToPartConverter.convert(List.of(document)))
        .thenAnswer(invocation -> List.of(partFromDocument));

    ArgumentCaptor<Message> sentMessageCaptor = ArgumentCaptor.forClass(Message.class);

    doAnswer(
            inv -> {
              consumerRef.get().accept(clientEvent, agentCard);
              return null;
            })
        .when(client)
        .sendMessage(sentMessageCaptor.capture());

    var actualResult = messageSender.sendMessage(agentCard, operation);

    assertThat(actualResult).isSameAs(expectedResult);

    Message sentMessage = sentMessageCaptor.getValue();
    assertThat(sentMessage.getParts())
        .satisfiesExactly(
            p -> assertThat(((TextPart) p).getText()).isEqualTo("hello"),
            p -> assertThat(p).isSameAs(partFromDocument));
    verify(client).close();
  }

  private SendMessageOperationConfiguration newSendMessageOperation(Duration timeout) {
    return new SendMessageOperationConfiguration(new Parameters("hello", null), timeout);
  }

  private MessageEvent newMessageEvent() {
    Message message =
        new Message.Builder()
            .messageId(MESSAGE_ID)
            .role(Message.Role.AGENT)
            .parts(List.of(new TextPart("Hi")))
            .build();
    return new MessageEvent(message);
  }

  private A2aMessageResult messageResult(String messageId) {
    var message =
        A2aMessage.builder()
            .messageId(messageId)
            .role(A2aMessage.Role.AGENT)
            .contextId("ctx-123")
            .contents(List.of(new TextContent("content", null)))
            .build();
    return new A2aMessageResult(message);
  }

  private A2aTaskResult taskResult(String taskId, TaskState state) {
    var status = A2aTaskStatus.builder().state(state).build();
    var task = A2aTask.builder().id(taskId).contextId("ctx-123").status(status).build();
    return new A2aTaskResult(task);
  }

  private void mockClientSendMessage(ClientEvent clientEvent) throws A2AClientException {
    doAnswer(
            inv -> {
              consumerRef.get().accept(clientEvent, agentCard);
              return null;
            })
        .when(client)
        .sendMessage(any());
  }
}
