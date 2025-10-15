/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.convert;

import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.model.message.content.Content;
import java.util.List;

public class A2aSdkObjectConverterImpl implements A2aSdkObjectConverter {

  private final A2aPartToContentConverter partToContentConverter;

  public A2aSdkObjectConverterImpl(A2aPartToContentConverter partToContentConverter) {
    this.partToContentConverter = partToContentConverter;
  }

  @Override
  public A2aMessage convert(Message message) {
    List<Content> contents = partToContentConverter.convert(message.getParts());
    return A2aMessage.builder()
        .role(
            message.getRole() == Message.Role.AGENT ? A2aMessage.Role.AGENT : A2aMessage.Role.USER)
        .messageId(message.getMessageId())
        .contextId(message.getContextId())
        .referenceTaskIds(message.getReferenceTaskIds())
        .taskId(message.getTaskId())
        .metadata(message.getMetadata())
        .contents(contents)
        .build();
  }

  @Override
  public A2aTask convert(Task task) {
    return A2aTask.builder()
        .id(task.getId())
        .contextId(task.getContextId())
        .status(convertStatus(task.getStatus()))
        .metadata(task.getMetadata())
        .artifacts(convertArtifacts(task))
        .history(convertHistory(task.getHistory()))
        .build();
  }

  private List<A2aMessage> convertHistory(List<Message> history) {
    if (history == null) {
      return List.of();
    }
    return history.stream().map(this::convert).toList();
  }

  private List<A2aArtifact> convertArtifacts(Task task) {
    if (task.getArtifacts() == null) {
      return List.of();
    }
    return task.getArtifacts().stream()
        .map(
            artifact ->
                A2aArtifact.builder()
                    .artifactId(artifact.artifactId())
                    .name(artifact.name())
                    .description(artifact.description())
                    .metadata(artifact.metadata())
                    .contents(partToContentConverter.convert(artifact.parts()))
                    .build())
        .toList();
  }

  private A2aTaskStatus convertStatus(TaskStatus status) {
    return A2aTaskStatus.builder()
        .state(A2aTaskStatus.TaskState.fromString(status.state().asString()))
        .message(status.message() != null ? convert(status.message()) : null)
        .timestamp(status.timestamp())
        .build();
  }
}
