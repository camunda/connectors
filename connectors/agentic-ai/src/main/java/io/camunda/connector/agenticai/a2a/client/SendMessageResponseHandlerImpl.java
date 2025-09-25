/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResultBuilder;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;

public class SendMessageResponseHandlerImpl implements SendMessageResponseHandler {

  public static final String FALLBACK_COMPLETION_MESSAGE = "Task completed.";
  private static final Set<TaskState> ERROR_STATES =
      Set.of(TaskState.FAILED, TaskState.CANCELED, TaskState.REJECTED, TaskState.UNKNOWN);

  private final PartsToContentConverter partsToContentConverter;

  public SendMessageResponseHandlerImpl(PartsToContentConverter partsToContentConverter) {
    this.partsToContentConverter = partsToContentConverter;
  }

  @Override
  public A2AClientSendMessageResult handleClientEvent(ClientEvent clientEvent) {
    switch (clientEvent) {
      case MessageEvent messageEvent -> {
        Message message = messageEvent.getMessage();
        List<Content> contents = partsToContentConverter.convert(message.getParts());
        return buildCompletedResult(message.getMessageId(), contents);
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
  public A2AClientSendMessageResult handleTask(Task task) {
    TaskStatus status = task.getStatus();
    switch (status.state()) {
      case COMPLETED -> {
        List<Content> contents = getContents(task);
        return buildCompletedResult(task.getId(), contents);
      }
      case SUBMITTED, WORKING -> {
        return buildResult(task, List.of());
      }
      case INPUT_REQUIRED -> {
        return buildResult(task, getContents(task));
      }
      default -> {
        if (ERROR_STATES.contains(status.state())) {
          List<Content> contents = getContents(task);
          if (CollectionUtils.isEmpty(contents)) {
            contents = createFallbackErrorContent(status);
          }
          return buildResult(task, contents);
        }

        throw new RuntimeException(
            "Task status %s is not supported yet.".formatted(status.state().asString()));
      }
    }
  }

  private List<Content> getContents(Task task) {
    // TODO: handle multiple artifacts
    List<Content> contentList = List.of();
    if (CollectionUtils.isNotEmpty(task.getArtifacts())) {
      contentList = partsToContentConverter.convert(task.getArtifacts().getFirst().parts());
    } else if (task.getStatus().message() != null) {
      contentList = partsToContentConverter.convert(task.getStatus().message().getParts());
    }
    return contentList;
  }

  private static A2AClientSendMessageResult buildCompletedResult(
      String responseId, List<Content> contentList) {
    return A2AClientSendMessageResultBuilder.builder()
        .responseId(responseId)
        .contents(
            contentList.isEmpty()
                ? List.of(TextContent.textContent(FALLBACK_COMPLETION_MESSAGE))
                : contentList)
        .state(A2AClientSendMessageResult.TaskState.COMPLETED)
        .build();
  }

  private static A2AClientSendMessageResult buildResult(Task task, List<Content> contents) {
    return A2AClientSendMessageResultBuilder.builder()
        .responseId(task.getId())
        .state(A2AClientSendMessageResult.TaskState.fromString(task.getStatus().state().asString()))
        .contents(contents)
        .build();
  }

  private static List<Content> createFallbackErrorContent(TaskStatus status) {
    return List.of(TextContent.textContent("Task ended with state: " + status.state()));
  }
}
