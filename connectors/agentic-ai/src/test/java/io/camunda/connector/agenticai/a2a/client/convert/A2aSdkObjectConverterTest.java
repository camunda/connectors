/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.model.message.content.Content;
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
class A2aSdkObjectConverterTest {

  @Mock private A2aPartToContentConverter partsToContentConverter;

  private A2aSdkObjectConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new A2aSdkObjectConverterImpl(partsToContentConverter);
  }

  @Nested
  class ConvertMessage {

    @Test
    void shouldConvertMessageWithBasicFields() {
      List<Content> contents = List.of(textContent("Hello from agent"));
      Message message =
          new Message.Builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("Hello from agent")))
              .build();

      when(partsToContentConverter.convert(message.getParts())).thenReturn(contents);

      A2aMessage result = converter.convert(message);

      assertThat(result.messageId()).isEqualTo("msg-123");
      assertThat(result.contextId()).isEqualTo("ctx-456");
      assertThat(result.role()).isEqualTo(A2aMessage.Role.AGENT);
      assertThat(result.contents()).isEqualTo(contents);
    }

    @Test
    void shouldIncludeAllMessageFields() {
      List<Content> contents = List.of(textContent("text"));
      Message message =
          new Message.Builder()
              .messageId("msg-1")
              .contextId("ctx-1")
              .taskId("task-1")
              .referenceTaskIds(List.of("ref-1", "ref-2"))
              .metadata(Map.of("key", "value"))
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("text")))
              .build();

      when(partsToContentConverter.convert(message.getParts())).thenReturn(contents);

      A2aMessage result = converter.convert(message);

      assertThat(result.messageId()).isEqualTo("msg-1");
      assertThat(result.contextId()).isEqualTo("ctx-1");
      assertThat(result.taskId()).isEqualTo("task-1");
      assertThat(result.referenceTaskIds()).containsExactly("ref-1", "ref-2");
      assertThat(result.metadata()).containsEntry("key", "value");
      assertThat(result.role()).isEqualTo(A2aMessage.Role.AGENT);
      assertThat(result.contents()).isEqualTo(contents);
    }
  }

  @Nested
  class ConvertTask {

    @ParameterizedTest
    @EnumSource(
        value = TaskState.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"INPUT_REQUIRED", "AUTH_REQUIRED"})
    void shouldConvertTaskWithAllSupportedStates(TaskState taskState) {
      Task task = createTask("task-" + taskState.name(), "ctx-1", taskState);

      A2aTask result = converter.convert(task);

      assertThat(result.status().state().name()).isEqualTo(taskState.name());
      assertThat(result.id()).isEqualTo("task-" + taskState.name());
      assertThat(result.contextId()).isEqualTo("ctx-1");
    }

    @Test
    void shouldIncludeTaskMetadata() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      when(task.getMetadata()).thenReturn(Map.of("meta-key", "meta-value"));

      A2aTask result = converter.convert(task);

      assertThat(result.metadata()).containsEntry("meta-key", "meta-value");
    }

    @Test
    void shouldIncludeStatusTimestamp() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      OffsetDateTime timestamp = OffsetDateTime.now();
      TaskStatus status = task.getStatus();
      when(status.timestamp()).thenReturn(timestamp);

      A2aTask result = converter.convert(task);

      assertThat(result.status().timestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldConvertTaskArtifacts() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      List<Content> contents = List.of(textContent("artifact content"));

      Artifact artifact = mock(Artifact.class);
      when(artifact.artifactId()).thenReturn("art-1");
      when(artifact.name()).thenReturn("result.txt");
      when(artifact.description()).thenReturn("Task result");
      when(artifact.metadata()).thenReturn(Map.of("type", "text"));
      when(artifact.parts()).thenReturn(List.of(new TextPart("artifact content")));
      when(task.getArtifacts()).thenReturn(List.of(artifact));

      when(partsToContentConverter.convert(artifact.parts())).thenReturn(contents);

      A2aTask result = converter.convert(task);

      assertThat(result.artifacts())
          .satisfiesExactly(
              a2aArtifact -> {
                assertThat(a2aArtifact.artifactId()).isEqualTo("art-1");
                assertThat(a2aArtifact.name()).isEqualTo("result.txt");
                assertThat(a2aArtifact.description()).isEqualTo("Task result");
                assertThat(a2aArtifact.metadata()).containsEntry("type", "text");
                assertThat(a2aArtifact.contents()).isEqualTo(contents);
              });
    }

    @Test
    void shouldHandleMultipleArtifacts() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);

      Artifact artifact1 = createArtifact("art-1", "file1.txt");
      Artifact artifact2 = createArtifact("art-2", "file2.txt");
      when(task.getArtifacts()).thenReturn(List.of(artifact1, artifact2));

      List<Content> contents = List.of(textContent("content"));
      when(partsToContentConverter.convert(artifact1.parts())).thenReturn(contents);
      when(partsToContentConverter.convert(artifact2.parts())).thenReturn(contents);

      A2aTask result = converter.convert(task);

      assertThat(result.artifacts()).hasSize(2);
      assertThat(result.artifacts().get(0).artifactId()).isEqualTo("art-1");
      assertThat(result.artifacts().get(1).artifactId()).isEqualTo("art-2");
    }

    @Test
    void shouldIncludeStatusMessage() {
      Task task = createTask("task-1", "ctx-1", TaskState.COMPLETED);
      TaskStatus status = task.getStatus();

      List<Content> messageContents = List.of(textContent("Status update"));

      Message statusMessage =
          new Message.Builder()
              .messageId("status-msg-1")
              .contextId("ctx-1")
              .role(Message.Role.AGENT)
              .parts(List.of(new TextPart("Status update")))
              .build();

      when(status.message()).thenReturn(statusMessage);
      when(partsToContentConverter.convert(statusMessage.getParts())).thenReturn(messageContents);

      A2aTask result = converter.convert(task);

      assertThat(result.status().message()).isNotNull();
      assertThat(result.status().message().messageId()).isEqualTo("status-msg-1");
      assertThat(result.status().message().contents()).isEqualTo(messageContents);
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
