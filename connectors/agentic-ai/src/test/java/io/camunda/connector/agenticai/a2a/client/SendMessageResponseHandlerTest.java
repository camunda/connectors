/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMessageResponseHandlerTest {

  @Mock private PartsToContentConverter partsToContentConverter;
  @InjectMocks private SendMessageResponseHandlerImpl handler;

  private static final TextContent SAMPLE_CONTENT = new TextContent("Hello world");
  private static final TextPart SAMPLE_TEXT_PART = new TextPart("Hello world");
  private static final String MESSAGE_ID = "msg-123";

  @Nested
  class MessageEvents {
    @Test
    void handleClientEventMessageEventWithContents() {
      when(partsToContentConverter.convert(any())).thenReturn(List.of(SAMPLE_CONTENT));
      Message message =
          new Message.Builder()
              .messageId(MESSAGE_ID)
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("Hello world")))
              .build();
      MessageEvent messageEvent = new MessageEvent(message);

      A2AClientSendMessageResult result = handler.handleClientEvent(messageEvent);

      assertThat(result.responseId()).isEqualTo(MESSAGE_ID);
      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.COMPLETED);
      assertThat(result.contents()).containsExactly(SAMPLE_CONTENT);
    }

    @Test
    void handleClientEventMessageEventEmptyContentsUsesFallbackCompletionMessage() {
      when(partsToContentConverter.convert(any())).thenReturn(List.of());
      Message message =
          new Message.Builder()
              .messageId(MESSAGE_ID)
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("")))
              .build();
      MessageEvent messageEvent = new MessageEvent(message);

      A2AClientSendMessageResult result = handler.handleClientEvent(messageEvent);

      assertThat(result.responseId()).isEqualTo(MESSAGE_ID);
      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.COMPLETED);
      assertThat(result.contents())
          .containsExactly(
              new TextContent(SendMessageResponseHandlerImpl.FALLBACK_COMPLETION_MESSAGE));
    }
  }

  @Nested
  class TaskEvents {

    @Test
    void handleClientEventTaskEventWithSubmittedState() {
      Task task = createTask(TaskState.SUBMITTED);
      TaskEvent taskEvent = mock(TaskEvent.class);
      when(taskEvent.getTask()).thenReturn(task);

      A2AClientSendMessageResult result = handler.handleClientEvent(taskEvent);

      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.SUBMITTED);
      assertThat(result.responseId()).isEqualTo("task-submitted");
      assertThat(result.contents()).isEmpty();
    }

    @Test
    void handleTaskCompletedWithTaskContents() {
      Task task = createTask(TaskState.COMPLETED);
      attachArtifact(task, List.of(SAMPLE_TEXT_PART), List.of(SAMPLE_CONTENT));

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.COMPLETED);
      assertThat(result.responseId()).isEqualTo("task-completed");
      assertThat(result.contents()).containsExactly(SAMPLE_CONTENT);
      verify(task.getStatus(), never()).message();
    }

    @Test
    void handleTaskCompletedFallsBackToStatusContents() {
      Task task = createTask(TaskState.COMPLETED);
      final var content = new TextContent("Status message content");
      attachStatusMessage(task, List.of(new TextPart(content.text())), List.of(content));

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.COMPLETED);
      assertThat(result.responseId()).isEqualTo("task-completed");
      assertThat(result.contents()).containsExactly(content);
      verify(task.getStatus(), atLeast(1)).message();
    }

    @Test
    void handleTaskCompletedWithoutContentsFallbackMessage() {
      Task task = createTask(TaskState.COMPLETED);
      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.COMPLETED);
      assertThat(result.contents())
          .containsExactly(
              new TextContent(SendMessageResponseHandlerImpl.FALLBACK_COMPLETION_MESSAGE));
      verify(partsToContentConverter, never()).convert(any());
    }

    @ParameterizedTest
    @EnumSource(
        value = TaskState.class,
        names = {"SUBMITTED", "WORKING"})
    void handleTaskSubmittedOrWorking(TaskState taskState) {
      Task task = createTask(taskState);

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state())
          .isEqualTo(A2AClientSendMessageResult.TaskState.fromString(taskState.asString()));
      assertThat(result.contents()).isEmpty();
    }

    @Test
    void handleTaskInputRequiredWithStatusContents() {
      Task task = createTask(TaskState.INPUT_REQUIRED);
      attachStatusMessage(task, List.of(SAMPLE_TEXT_PART), List.of(SAMPLE_CONTENT));

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(A2AClientSendMessageResult.TaskState.INPUT_REQUIRED);
      assertThat(result.contents()).containsExactly(SAMPLE_CONTENT);
    }

    @ParameterizedTest
    @MethodSource("errorStatesNoContentScenarios")
    void handleTaskErrorWithoutContentsUsesFallbackErrorMessage(
        TaskState taskState,
        A2AClientSendMessageResult.TaskState expectedState,
        Content expectedContent) {
      Task task = createTask(taskState);

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(expectedState);
      assertThat(result.contents()).containsExactly(expectedContent);
      verify(partsToContentConverter, never()).convert(any());
    }

    @ParameterizedTest
    @MethodSource("errorStatesWithContentScenarios")
    void handleTaskErrorWithContents(
        TaskState taskState, A2AClientSendMessageResult.TaskState expectedState) {
      Task task = createTask(taskState);
      attachArtifact(task, List.of(SAMPLE_TEXT_PART), List.of(SAMPLE_CONTENT));

      A2AClientSendMessageResult result = handler.handleTask(task);

      assertThat(result.state()).isEqualTo(expectedState);
      assertThat(result.contents()).containsExactly(SAMPLE_CONTENT);
      verify(task.getStatus(), never()).message();
    }

    @Test
    void handleTaskAuthRequiredThrowsUnsupported() {
      Task task = createTask(TaskState.AUTH_REQUIRED);

      assertThatThrownBy(() -> handler.handleTask(task))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Task status auth-required is not supported yet.");
    }

    private Task createTask(TaskState state) {
      TaskStatus status = mock(TaskStatus.class);
      when(status.state()).thenReturn(state);

      Task task = mock(Task.class);
      lenient().when(task.getStatus()).thenReturn(status);
      lenient().when(task.getId()).thenReturn("task-" + state.asString());
      return task;
    }

    private void attachStatusMessage(
        Task task, List<Part<?>> rawParts, List<Content> convertedContents) {
      Message msg = mock(Message.class);
      when(msg.getParts()).thenReturn(rawParts);

      TaskStatus status = task.getStatus();
      when(status.message()).thenReturn(msg);

      when(partsToContentConverter.convert(rawParts)).thenReturn(convertedContents);
    }

    private void attachArtifact(
        Task task, List<Part<?>> rawParts, List<Content> convertedContents) {
      Artifact artifact = mock(Artifact.class);
      when(artifact.parts()).thenReturn(rawParts);
      when(task.getArtifacts()).thenReturn(List.of(artifact));

      when(partsToContentConverter.convert(rawParts)).thenReturn(convertedContents);
    }

    static Stream<Arguments> errorStatesNoContentScenarios() {
      return Stream.of(
          Arguments.of(
              TaskState.FAILED,
              A2AClientSendMessageResult.TaskState.FAILED,
              new TextContent("Task ended with state: FAILED")),
          Arguments.of(
              TaskState.CANCELED,
              A2AClientSendMessageResult.TaskState.CANCELED,
              new TextContent("Task ended with state: CANCELED")),
          Arguments.of(
              TaskState.REJECTED,
              A2AClientSendMessageResult.TaskState.REJECTED,
              new TextContent("Task ended with state: REJECTED")),
          Arguments.of(
              TaskState.UNKNOWN,
              A2AClientSendMessageResult.TaskState.UNKNOWN,
              new TextContent("Task ended with state: UNKNOWN")));
    }

    static Stream<Arguments> errorStatesWithContentScenarios() {
      return errorStatesNoContentScenarios();
    }
  }
}
