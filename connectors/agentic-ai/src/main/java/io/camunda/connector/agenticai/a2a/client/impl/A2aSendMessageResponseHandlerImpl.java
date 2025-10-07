/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aContent;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import java.util.List;

public class A2aSendMessageResponseHandlerImpl implements A2aSendMessageResponseHandler {

  private final PartsToContentConverter partsToContentConverter;

  public A2aSendMessageResponseHandlerImpl(PartsToContentConverter partsToContentConverter) {
    this.partsToContentConverter = partsToContentConverter;
  }

  @Override
  public A2aSendMessageResult handleClientEvent(ClientEvent clientEvent) {
    switch (clientEvent) {
      case MessageEvent messageEvent -> {
        Message message = messageEvent.getMessage();
        return new A2aSendMessageResult.A2aMessageResult(buildMessage(message));
      }
      case TaskEvent taskEvent -> {
        Task task = taskEvent.getTask();
        return handleTask(task);
      }
      default ->
          throw new RuntimeException(
              "Only message events and completed tasks are supported in the response yet.");
    }
  }

  @Override
  public A2aSendMessageResult.A2aTaskResult handleTask(Task task) {
    TaskStatus status = task.getStatus();
    if (status.state() == TaskState.INPUT_REQUIRED || status.state() == TaskState.AUTH_REQUIRED) {
      throw new RuntimeException(
          "Task status %s is not supported yet.".formatted(status.state().asString()));
    }

    return new A2aSendMessageResult.A2aTaskResult(buildTask(task));
  }

  private A2aMessage buildMessage(Message message) {
    List<A2aContent> contents = partsToContentConverter.convert(message.getParts());
    return A2aMessage.builder()
        .role(A2aMessage.Role.AGENT)
        .messageId(message.getMessageId())
        .contextId(message.getContextId())
        .referenceTaskIds(message.getReferenceTaskIds())
        .taskId(message.getTaskId())
        .metadata(message.getMetadata())
        .contents(contents)
        .build();
  }

  private A2aTask buildTask(Task task) {
    return A2aTask.builder()
        .taskId(task.getId())
        .contextId(task.getContextId())
        .status(buildStatus(task.getStatus()))
        .metadata(task.getMetadata())
        .artifacts(buildArtifacts(task))
        .build();
  }

  private List<A2aArtifact> buildArtifacts(Task task) {
    return task.getArtifacts().stream()
        .map(
            artifact ->
                A2aArtifact.builder()
                    .artifactId(artifact.artifactId())
                    .name(artifact.name())
                    .description(artifact.description())
                    .metadata(artifact.metadata())
                    .contents(partsToContentConverter.convert(artifact.parts()))
                    .build())
        .toList();
  }

  private A2aTaskStatus buildStatus(TaskStatus status) {
    return A2aTaskStatus.builder()
        .state(A2aTaskStatus.TaskState.fromString(status.state().asString()))
        .message(status.message() != null ? buildMessage(status.message()) : null)
        .timestamp(status.timestamp())
        .build();
  }
}
