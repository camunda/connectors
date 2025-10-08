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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.convert.A2aPartToContentConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aContent;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import java.time.OffsetDateTime;
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

  @Mock private A2aPartToContentConverter partsToContentConverter;

  private A2aSendMessageResponseHandlerImpl handler;

  @BeforeEach
  void setUp() {
    handler = new A2aSendMessageResponseHandlerImpl(partsToContentConverter);
  }

  @Nested
  class HandleClientEvent {

    @Test
    void shouldConvertMessageEventToMessageResult() {
      List<Part<?>> parts = List.of(new TextPart("Hello from agent"));
      List<A2aContent> contents =
          List.of(A2aContent.builder().content(textContent("Hello from agent")).build());
      Message message =
          new Message.Builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(Message.Role.AGENT)
              .parts(parts)
              .build();
      MessageEvent event = new MessageEvent(message);

      when(partsToContentConverter.convert(parts)).thenReturn(contents);

      A2aSendMessageResult result = handler.handleClientEvent(event);

      assertThat(result).isInstanceOf(A2aSendMessageResult.A2aMessageResult.class);
      A2aSendMessageResult.A2aMessageResult messageResult =
          (A2aSendMessageResult.A2aMessageResult) result;
      A2aMessage resultMessage = messageResult.message();

      A2aMessage expectedMessage =
          A2aMessage.builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(A2aMessage.Role.AGENT)
              .contents(contents)
              .build();

      assertThat(resultMessage).isEqualTo(expectedMessage);
    }

    @Test
    void shouldIncludeAllMessageFields() {
      List<Part<?>> parts = List.of(new TextPart("text"));
      List<A2aContent> contents =
          List.of(A2aContent.builder().content(textContent("text")).build());
      Message message =
          new Message.Builder()
              .messageId("msg-1")
              .contextId("ctx-1")
              .taskId("task-1")
              .referenceTaskIds(List.of("ref-1", "ref-2"))
              .metadata(Map.of("key", "value"))
              .role(Message.Role.AGENT)
              .parts(parts)
              .build();
      MessageEvent event = new MessageEvent(message);

      when(partsToContentConverter.convert(parts)).thenReturn(contents);

      A2aSendMessageResult result = handler.handleClientEvent(event);

      A2aSendMessageResult.A2aMessageResult messageResult =
          (A2aSendMessageResult.A2aMessageResult) result;
      A2aMessage resultMessage = messageResult.message();

      A2aMessage expectedMessage =
          A2aMessage.builder()
              .messageId("msg-1")
              .contextId("ctx-1")
              .taskId("task-1")
              .referenceTaskIds(List.of("ref-1", "ref-2"))
              .metadata(Map.of("key", "value"))
              .role(A2aMessage.Role.AGENT)
              .contents(contents)
              .build();

      assertThat(resultMessage).isEqualTo(expectedMessage);
    }

    @Test
    void shouldConvertTaskEventToTaskResult() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      TaskEvent event = new TaskEvent(task);

      A2aSendMessageResult result = handler.handleClientEvent(event);

      assertThat(result).isInstanceOf(A2aSendMessageResult.A2aTaskResult.class);
      A2aSendMessageResult.A2aTaskResult taskResult = (A2aSendMessageResult.A2aTaskResult) result;

      assertThat(taskResult.task().taskId()).isEqualTo("task-1");
      assertThat(taskResult.task().contextId()).isEqualTo("ctx-1");
      assertThat(taskResult.task().status().state()).isEqualTo(A2aTaskStatus.TaskState.COMPLETED);
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

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      assertThat(result.task().status().state().name()).isEqualTo(taskState.name());
      assertThat(result.task().taskId()).isEqualTo("task-" + taskState.name());
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

    @Test
    void shouldIncludeTaskMetadata() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      when(task.getMetadata()).thenReturn(Map.of("meta-key", "meta-value"));

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      assertThat(result.task().metadata()).containsEntry("meta-key", "meta-value");
    }

    @Test
    void shouldIncludeStatusTimestamp() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      OffsetDateTime timestamp = OffsetDateTime.now();
      TaskStatus status = task.getStatus();
      when(status.timestamp()).thenReturn(timestamp);

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      assertThat(result.task().status().timestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldConvertTaskArtifacts() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      List<Part<?>> parts = List.of(new TextPart("artifact content"));
      List<A2aContent> contents =
          List.of(A2aContent.builder().content(textContent("artifact content")).build());

      Artifact artifact = mock(Artifact.class);
      when(artifact.artifactId()).thenReturn("art-1");
      when(artifact.name()).thenReturn("result.txt");
      when(artifact.description()).thenReturn("Task result");
      when(artifact.metadata()).thenReturn(Map.of("type", "text"));
      when(artifact.parts()).thenReturn(parts);
      when(task.getArtifacts()).thenReturn(List.of(artifact));

      when(partsToContentConverter.convert(parts)).thenReturn(contents);

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      A2aArtifact expectedArtifact =
          A2aArtifact.builder()
              .artifactId("art-1")
              .name("result.txt")
              .description("Task result")
              .metadata(Map.of("type", "text"))
              .contents(contents)
              .build();
      assertThat(result.task().artifacts())
          .satisfiesExactly(a2aArtifact -> assertThat(a2aArtifact).isEqualTo(expectedArtifact));
    }

    @Test
    void shouldHandleMultipleArtifacts() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);

      Artifact artifact1 = createArtifact("art-1", "file1.txt");
      Artifact artifact2 = createArtifact("art-2", "file2.txt");
      when(task.getArtifacts()).thenReturn(List.of(artifact1, artifact2));

      List<A2aContent> contents =
          List.of(A2aContent.builder().content(textContent("content")).build());
      when(partsToContentConverter.convert(anyList())).thenReturn(contents);

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      assertThat(result.task().artifacts()).hasSize(2);
      assertThat(result.task().artifacts().get(0).artifactId()).isEqualTo("art-1");
      assertThat(result.task().artifacts().get(1).artifactId()).isEqualTo("art-2");
    }

    @Test
    void shouldIncludeStatusMessage() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      TaskStatus status = task.getStatus();

      List<Part<?>> messageParts = List.of(new TextPart("Status update"));
      List<A2aContent> messageContents =
          List.of(A2aContent.builder().content(textContent("Status update")).build());

      Message statusMessage =
          new Message.Builder()
              .messageId("status-msg-1")
              .contextId("ctx-1")
              .role(Message.Role.AGENT)
              .parts(messageParts)
              .build();

      when(status.message()).thenReturn(statusMessage);
      when(partsToContentConverter.convert(messageParts)).thenReturn(messageContents);

      A2aSendMessageResult.A2aTaskResult result = handler.handleTask(task);

      assertThat(result.task().status().message()).isNotNull();
      assertThat(result.task().status().message().messageId()).isEqualTo("status-msg-1");
      assertThat(result.task().status().message().contents()).isEqualTo(messageContents);
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

  private Artifact createArtifact(String id, String name) {
    Artifact artifact = mock(Artifact.class);
    when(artifact.artifactId()).thenReturn(id);
    when(artifact.name()).thenReturn(name);
    when(artifact.parts()).thenReturn(List.of(new TextPart("content")));
    return artifact;
  }
}
