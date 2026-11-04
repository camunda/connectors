/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.convert;

import static io.camunda.connector.agenticai.a2a.client.common.model.part.A2aTextPart.textPart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aSdkObjectConverterTest {

  @Mock private A2aPartConverter partConverter;

  private A2aSdkObjectConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new A2aSdkObjectConverterImpl(partConverter);
  }

  @Nested
  class ConvertMessage {

    @Test
    void shouldConvertMessageWithBasicFields() {
      List<A2aPart> contents = List.of(textPart("Hello from agent"));
      Message message =
          Message.builder()
              .messageId("msg-123")
              .contextId("ctx-456")
              .role(Message.Role.ROLE_AGENT)
              .parts(List.of(new TextPart("Hello from agent", null)))
              .build();

      when(partConverter.convert(message.parts())).thenReturn(contents);

      A2aMessage result = converter.convert(message);

      assertThat(result.messageId()).isEqualTo("msg-123");
      assertThat(result.contextId()).isEqualTo("ctx-456");
      assertThat(result.role()).isEqualTo(A2aMessage.Role.AGENT);
      assertThat(result.contents()).isEqualTo(contents);
    }

    @Test
    void shouldConvertUserRole() {
      Message message =
          Message.builder()
              .messageId("msg-1")
              .contextId("ctx-1")
              .role(Message.Role.ROLE_USER)
              .parts(List.of(new TextPart("text", null)))
              .build();
      when(partConverter.convert(message.parts())).thenReturn(List.of(textPart("text")));

      assertThat(converter.convert(message).role()).isEqualTo(A2aMessage.Role.USER);
    }

    @Test
    void shouldIncludeAllMessageFields() {
      List<A2aPart> contents = List.of(textPart("text"));
      Message message =
          Message.builder()
              .messageId("msg-1")
              .contextId("ctx-1")
              .taskId("task-1")
              .referenceTaskIds(List.of("ref-1", "ref-2"))
              .metadata(Map.of("key", "value"))
              .role(Message.Role.ROLE_AGENT)
              .parts(List.of(new TextPart("text", null)))
              .build();

      when(partConverter.convert(message.parts())).thenReturn(contents);

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
    @CsvSource({
      "TASK_STATE_SUBMITTED, SUBMITTED",
      "TASK_STATE_WORKING, WORKING",
      "TASK_STATE_INPUT_REQUIRED, INPUT_REQUIRED",
      "TASK_STATE_AUTH_REQUIRED, AUTH_REQUIRED",
      "TASK_STATE_COMPLETED, COMPLETED",
      "TASK_STATE_CANCELED, CANCELED",
      "TASK_STATE_FAILED, FAILED",
      "TASK_STATE_REJECTED, REJECTED",
      "TASK_STATE_UNSPECIFIED, UNKNOWN"
    })
    void shouldMapAllTaskStates(TaskState sdkState, A2aTaskStatus.TaskState expectedState) {
      Task task = createTask("task-1", "ctx-1", sdkState).build();

      A2aTask result = converter.convert(task);

      assertThat(result.status().state()).isEqualTo(expectedState);
      assertThat(result.id()).isEqualTo("task-1");
      assertThat(result.contextId()).isEqualTo("ctx-1");
      assertThat(result.history()).isEmpty();
    }

    @Test
    void shouldIncludeTaskMetadata() {
      Task task =
          createTask("task-1", "ctx-1", TaskState.TASK_STATE_COMPLETED)
              .metadata(Map.of("meta-key", "meta-value"))
              .build();

      A2aTask result = converter.convert(task);

      assertThat(result.metadata()).containsEntry("meta-key", "meta-value");
    }

    @Test
    void shouldIncludeStatusTimestamp() {
      OffsetDateTime timestamp = OffsetDateTime.now();
      Task task =
          Task.builder()
              .id("task-1")
              .contextId("ctx-1")
              .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED, null, timestamp))
              .build();

      A2aTask result = converter.convert(task);

      assertThat(result.status().timestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldConvertTaskArtifacts() {
      List<A2aPart> contents = List.of(textPart("artifact content"));
      Artifact artifact =
          Artifact.builder()
              .artifactId("art-1")
              .name("result.txt")
              .description("Task result")
              .metadata(Map.of("type", "text"))
              .parts(List.of(new TextPart("artifact content", null)))
              .build();
      Task task =
          createTask("task-1", "ctx-1", TaskState.TASK_STATE_COMPLETED)
              .artifacts(List.of(artifact))
              .build();

      when(partConverter.convert(artifact.parts())).thenReturn(contents);

      A2aTask result = converter.convert(task);

      assertThat(result.artifacts())
          .satisfiesExactly(a2aArtifact -> {
            assertThat(a2aArtifact.artifactId()).isEqualTo("art-1");
            assertThat(a2aArtifact.name()).isEqualTo("result.txt");
            assertThat(a2aArtifact.description()).isEqualTo("Task result");
            assertThat(a2aArtifact.metadata()).containsEntry("type", "text");
            assertThat(a2aArtifact.contents()).isEqualTo(contents);
          });
    }

    @Test
    void shouldHandleMultipleArtifacts() {
      Artifact artifact1 = createArtifact("art-1", "file1.txt");
      Artifact artifact2 = createArtifact("art-2", "file2.txt");
      Task task =
          createTask("task-1", "ctx-1", TaskState.TASK_STATE_COMPLETED)
              .artifacts(List.of(artifact1, artifact2))
              .build();

      List<A2aPart> contents = List.of(textPart("content"));
      when(partConverter.convert(artifact1.parts())).thenReturn(contents);
      when(partConverter.convert(artifact2.parts())).thenReturn(contents);

      A2aTask result = converter.convert(task);

      assertThat(result.artifacts()).hasSize(2);
      assertThat(result.artifacts().get(0).artifactId()).isEqualTo("art-1");
      assertThat(result.artifacts().get(1).artifactId()).isEqualTo("art-2");
    }

    @Test
    void shouldIncludeStatusMessage() {
      List<A2aPart> messageContents = List.of(textPart("Status update"));

      Message statusMessage =
          Message.builder()
              .messageId("status-msg-1")
              .contextId("ctx-1")
              .role(Message.Role.ROLE_AGENT)
              .parts(List.of(new TextPart("Status update", null)))
              .build();
      Task task =
          Task.builder()
              .id("task-1")
              .contextId("ctx-1")
              .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED, statusMessage, null))
              .build();

      when(partConverter.convert(statusMessage.parts())).thenReturn(messageContents);

      A2aTask result = converter.convert(task);

      assertThat(result.status().message()).isNotNull();
      assertThat(result.status().message().messageId()).isEqualTo("status-msg-1");
      assertThat(result.status().message().contents()).isEqualTo(messageContents);
    }

    @Test
    void shouldConvertSingleHistoryMessage() {
      Message historyMessage =
          Message.builder()
              .messageId("hist-1")
              .contextId("ctx-h")
              .taskId("task-h-one")
              .role(Message.Role.ROLE_USER)
              .parts(List.of(new TextPart("History one", null)))
              .build();
      Task task =
          createTask("task-h-one", "ctx-h", TaskState.TASK_STATE_COMPLETED)
              .history(List.of(historyMessage))
              .build();

      List<A2aPart> convertedContent = List.of(textPart("History one"));
      when(partConverter.convert(historyMessage.parts())).thenReturn(convertedContent);

      A2aTask result = converter.convert(task);

      assertThat(result.history())
          .satisfiesExactly(message -> {
            assertThat(message.messageId()).isEqualTo("hist-1");
            assertThat(message.contextId()).isEqualTo("ctx-h");
            assertThat(message.taskId()).isEqualTo("task-h-one");
            assertThat(message.role()).isEqualTo(A2aMessage.Role.USER);
            assertThat(message.contents()).isEqualTo(convertedContent);
          });
    }

    @Test
    void shouldConvertMultipleHistoryMessagesPreservingOrder() {
      Message m1 =
          Message.builder()
              .messageId("hist-1")
              .contextId("ctx-h")
              .taskId("task-h-multi")
              .role(Message.Role.ROLE_USER)
              .parts(List.of(new TextPart("First", null)))
              .build();
      Message m2 =
          Message.builder()
              .messageId("hist-2")
              .contextId("ctx-h")
              .taskId("task-h-multi")
              .role(Message.Role.ROLE_AGENT)
              .parts(List.of(new TextPart("Second", null)))
              .metadata(Map.of("type", "text"))
              .build();
      Task task =
          createTask("task-h-multi", "ctx-h", TaskState.TASK_STATE_COMPLETED)
              .history(List.of(m1, m2))
              .build();

      when(partConverter.convert(m1.parts())).thenReturn(List.of(textPart("First")));
      when(partConverter.convert(m2.parts())).thenReturn(List.of(textPart("Second")));

      A2aTask result = converter.convert(task);

      assertThat(result.history())
          .satisfiesExactly(
              message -> {
                assertThat(message.messageId()).isEqualTo("hist-1");
                assertThat(message.role()).isEqualTo(A2aMessage.Role.USER);
                assertThat(message.contents()).isEqualTo(List.of(textPart("First")));
              },
              message -> {
                assertThat(message.messageId()).isEqualTo("hist-2");
                assertThat(message.role()).isEqualTo(A2aMessage.Role.AGENT);
                assertThat(message.metadata()).containsEntry("type", "text");
                assertThat(message.contents()).isEqualTo(List.of(textPart("Second")));
              });
    }
  }

  private static Task.Builder createTask(String taskId, String contextId, TaskState state) {
    return Task.builder().id(taskId).contextId(contextId).status(new TaskStatus(state, null, null));
  }

  private static Artifact createArtifact(String id, String name) {
    return Artifact.builder()
        .artifactId(id)
        .name(name)
        .parts(List.of(new TextPart("content", null)))
        .build();
  }
}
