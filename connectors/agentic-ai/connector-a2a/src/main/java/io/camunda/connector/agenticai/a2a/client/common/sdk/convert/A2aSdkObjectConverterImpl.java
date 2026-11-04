/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.convert;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import java.util.List;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.jspecify.annotations.Nullable;

public class A2aSdkObjectConverterImpl implements A2aSdkObjectConverter {

  private final A2aPartConverter partConverter;

  public A2aSdkObjectConverterImpl(A2aPartConverter partConverter) {
    this.partConverter = partConverter;
  }

  @Override
  public A2aMessage convert(Message message) {
    List<A2aPart> contents = partConverter.convert(message.parts());
    return A2aMessage.builder()
        .role(
            message.role() == Message.Role.ROLE_AGENT
                ? A2aMessage.Role.AGENT
                : A2aMessage.Role.USER)
        .messageId(message.messageId())
        .contextId(message.contextId())
        .referenceTaskIds(message.referenceTaskIds())
        .taskId(message.taskId())
        .metadata(message.metadata())
        .contents(contents)
        .build();
  }

  @Override
  public A2aTask convert(Task task) {
    return A2aTask.builder()
        .id(task.id())
        .contextId(task.contextId())
        .status(convertStatus(task.status()))
        .metadata(task.metadata())
        .artifacts(convertArtifacts(task))
        .history(convertHistory(task.history()))
        .build();
  }

  private List<A2aMessage> convertHistory(@Nullable List<Message> history) {
    if (history == null) {
      return List.of();
    }
    return history.stream().map(this::convert).toList();
  }

  private List<A2aArtifact> convertArtifacts(Task task) {
    if (task.artifacts() == null) {
      return List.of();
    }
    return task.artifacts().stream()
        .map(artifact ->
            A2aArtifact.builder()
                .artifactId(artifact.artifactId())
                .name(artifact.name())
                .description(artifact.description())
                .metadata(artifact.metadata())
                .contents(partConverter.convert(artifact.parts()))
                .build())
        .toList();
  }

  private static A2aTaskStatus.TaskState convertState(TaskState state) {
    return switch (state) {
      case TASK_STATE_SUBMITTED -> A2aTaskStatus.TaskState.SUBMITTED;
      case TASK_STATE_WORKING -> A2aTaskStatus.TaskState.WORKING;
      case TASK_STATE_INPUT_REQUIRED -> A2aTaskStatus.TaskState.INPUT_REQUIRED;
      case TASK_STATE_AUTH_REQUIRED -> A2aTaskStatus.TaskState.AUTH_REQUIRED;
      case TASK_STATE_COMPLETED -> A2aTaskStatus.TaskState.COMPLETED;
      case TASK_STATE_CANCELED -> A2aTaskStatus.TaskState.CANCELED;
      case TASK_STATE_FAILED -> A2aTaskStatus.TaskState.FAILED;
      case TASK_STATE_REJECTED -> A2aTaskStatus.TaskState.REJECTED;
      case TASK_STATE_UNSPECIFIED -> A2aTaskStatus.TaskState.UNKNOWN;
    };
  }

  private A2aTaskStatus convertStatus(TaskStatus status) {
    return A2aTaskStatus.builder()
        .state(convertState(status.state()))
        .message(status.message() != null ? convert(status.message()) : null)
        .timestamp(status.timestamp())
        .build();
  }
}
