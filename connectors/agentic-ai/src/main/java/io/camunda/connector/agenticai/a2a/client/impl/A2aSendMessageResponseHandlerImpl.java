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
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;

public class A2aSendMessageResponseHandlerImpl implements A2aSendMessageResponseHandler {

  private final A2aSdkObjectConverter sdkObjectConverter;

  public A2aSendMessageResponseHandlerImpl(A2aSdkObjectConverter sdkObjectConverter) {
    this.sdkObjectConverter = sdkObjectConverter;
  }

  @Override
  public A2aSendMessageResult handleClientEvent(ClientEvent clientEvent) {
    switch (clientEvent) {
      case MessageEvent messageEvent -> {
        Message message = messageEvent.getMessage();
        return sdkObjectConverter.convert(message);
      }
      case TaskEvent taskEvent -> {
        Task task = taskEvent.getTask();
        return handleTask(task);
      }
      default ->
          throw new RuntimeException("Only message and task events are supported in the response.");
    }
  }

  @Override
  public A2aTask handleTask(Task task) {
    TaskStatus status = task.getStatus();
    if (status.state() == TaskState.INPUT_REQUIRED || status.state() == TaskState.AUTH_REQUIRED) {
      throw new RuntimeException(
          "Task status %s is not supported yet.".formatted(status.state().asString()));
    }

    return sdkObjectConverter.convert(task);
  }
}
