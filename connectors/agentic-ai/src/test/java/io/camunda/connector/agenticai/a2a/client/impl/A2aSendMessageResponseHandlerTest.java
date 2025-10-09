/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aSendMessageResponseHandlerTest {

  @Mock private A2aSdkObjectConverter sdkObjectConverter;

  private A2aSendMessageResponseHandlerImpl handler;

  @BeforeEach
  void setUp() {
    handler = new A2aSendMessageResponseHandlerImpl(sdkObjectConverter);
  }

  @Nested
  class HandleClientEvent {

    @Test
    void shouldConvertMessageEventToMessageResult() {
      Message message =
          new Message.Builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("Hello from agent")))
              .build();
      MessageEvent event = new MessageEvent(message);

      A2aMessage expectedMessage =
          A2aMessage.builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(A2aMessage.Role.AGENT)
              .contents(List.of(textContent("Hello from agent")))
              .build();
      when(sdkObjectConverter.convert(message)).thenReturn(expectedMessage);

      A2aSendMessageResult result = handler.handleClientEvent(event);

      assertThat(result).isInstanceOf(A2aMessage.class);
      assertThat(result).isEqualTo(expectedMessage);
    }

    @Test
    void shouldConvertTaskEventToTaskResult() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      TaskEvent event = new TaskEvent(task);

      A2aTask a2aTask =
          A2aTask.builder()
              .id("task-1")
              .contextId("ctx-1")
              .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.COMPLETED).build())
              .build();
      when(sdkObjectConverter.convert(task)).thenReturn(a2aTask);

      A2aSendMessageResult result = handler.handleClientEvent(event);

      assertThat(result).isInstanceOf(A2aTask.class);
      assertThat(result).isEqualTo(a2aTask);
    }

    @Test
    void shouldThrowForUnsupportedEventType() {
      ClientEvent unsupportedEvent = mock(TaskUpdateEvent.class);

      assertThatThrownBy(() -> handler.handleClientEvent(unsupportedEvent))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Only message and task events are supported in the response.");
    }
  }

  @Nested
  class HandleTask {

    @ParameterizedTest
    @EnumSource(
        value = TaskState.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"INPUT_REQUIRED", "AUTH_REQUIRED"})
    void shouldHandleSupportedTaskStates(TaskState taskState) {
      Task task = createTask("task-" + taskState.name(), "ctx-1", taskState);

      A2aTask expectedTask =
          A2aTask.builder()
              .id("task-" + taskState.name())
              .contextId("ctx-1")
              .status(
                  A2aTaskStatus.builder()
                      .state(A2aTaskStatus.TaskState.valueOf(taskState.name()))
                      .build())
              .build();
      when(sdkObjectConverter.convert(task)).thenReturn(expectedTask);

      A2aTask result = handler.handleTask(task);

      assertThat(result).isEqualTo(expectedTask);
    }

    @ParameterizedTest
    @EnumSource(
        value = TaskState.class,
        names = {"INPUT_REQUIRED", "AUTH_REQUIRED"})
    void shouldThrowForNotSupportedStates(TaskState taskState) {
      Task task = createTask("task-input", "ctx-1", taskState);

      assertThatThrownBy(() -> handler.handleTask(task))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Task status %s is not supported yet.".formatted(taskState.asString()));
    }
  }

  private Task createTask(String taskId, String contextId, TaskState state) {
    TaskStatus status = mock(TaskStatus.class);
    when(status.state()).thenReturn(state);
    lenient().when(status.message()).thenReturn(null);
    lenient().when(status.timestamp()).thenReturn(null);

    Task task = mock(Task.class);
    lenient().when(task.getId()).thenReturn(taskId);
    lenient().when(task.getContextId()).thenReturn(contextId);
    lenient().when(task.getStatus()).thenReturn(status);
    lenient().when(task.getMetadata()).thenReturn(Map.of());
    lenient().when(task.getArtifacts()).thenReturn(List.of());

    return task;
  }
}
