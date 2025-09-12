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
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public class SendMessageResultHandlerImpl implements SendMessageResultHandler {

  public static final String FALLBACK_COMPLETION_MESSAGE = "Task completed.";
  private final PartsToContentConverter partsToContentConverter;

  public SendMessageResultHandlerImpl(PartsToContentConverter partsToContentConverter) {
    this.partsToContentConverter = partsToContentConverter;
  }

  @Override
  public A2AClientSendMessageResult handleClientEvent(ClientEvent clientEvent) {
    if (clientEvent instanceof MessageEvent messageEvent) {
      Message message = messageEvent.getMessage();
      List<Content> contentList = partsToContentConverter.convert(message.getParts());
      return buildSendMessageResult(message.getMessageId(), contentList);
    } else {
      if (clientEvent instanceof TaskEvent taskEvent) {
        Task task = taskEvent.getTask();
        return handleTask(task);
      }
    }
    throw new ConnectorException(
        "Only message events and completed tasks are supported in the response yet.");
  }

  @Override
  public A2AClientSendMessageResult handleTask(Task task) {
    TaskStatus status = task.getStatus();
    if (status.state() == TaskState.COMPLETED) {
      List<Content> contentList = getContents(task);
      return buildSendMessageResult(task.getId(), contentList);
    } else if (status.state() == TaskState.SUBMITTED || status.state() == TaskState.WORKING) {
      return A2AClientSendMessageResultBuilder.builder()
          .responseId(task.getId())
          .state(A2AClientSendMessageResult.TaskState.fromString(status.state().asString()))
          .contentList(List.of())
          .build();
    } else if (status.state() == TaskState.FAILED
        || status.state() == TaskState.CANCELED
        || status.state() == TaskState.REJECTED) {
      A2AClientSendMessageResultBuilder.builder()
          .responseId(task.getId())
          .state(A2AClientSendMessageResult.TaskState.fromString(status.state().asString()))
          .contentList(getContents(task)) // TODO: include a default message if content is empty
          .build();
    }

    throw new ConnectorException(
        "Task status %s is not supported yet.".formatted(status.state().asString()));
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

  private static A2AClientSendMessageResult buildSendMessageResult(
      String responseId, List<Content> contentList) {
    return A2AClientSendMessageResultBuilder.builder()
        .responseId(responseId)
        .contentList(
            contentList.isEmpty()
                ? List.of(TextContent.textContent(FALLBACK_COMPLETION_MESSAGE))
                : contentList)
        .state(A2AClientSendMessageResult.TaskState.COMPLETED)
        .build();
  }
}
