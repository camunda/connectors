/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;

public class A2aSendMessageResponseHandlerImpl implements A2aSendMessageResponseHandler {

  private final A2aSdkObjectConverter sdkObjectConverter;

  public A2aSendMessageResponseHandlerImpl(A2aSdkObjectConverter sdkObjectConverter) {
    this.sdkObjectConverter = sdkObjectConverter;
  }

  @Override
  public A2aSendMessageResult handleClientEvent(ClientEvent clientEvent) {
    return switch (clientEvent) {
      case MessageEvent messageEvent -> sdkObjectConverter.convert(messageEvent.getMessage());
      case TaskEvent taskEvent -> handleTask(taskEvent.getTask());
      default ->
          throw new RuntimeException("Only message and task events are supported in the response.");
    };
  }

  @Override
  public A2aTask handleTask(Task task) {
    TaskStatus status = task.status();
    if (status.state() == TaskState.TASK_STATE_AUTH_REQUIRED) {
      throw new RuntimeException("Task status %s is not supported yet.".formatted(status.state()));
    }

    return sdkObjectConverter.convert(task);
  }
}
