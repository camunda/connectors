/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED;
import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED;

import io.a2a.client.Client;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import io.camunda.connector.api.error.ConnectorException;

public class A2aSdkClient implements AutoCloseable {
  private final Client sdkClient;
  private final ManagedChannelFactory channelFactory;

  public A2aSdkClient(Client sdkClient, ManagedChannelFactory channelFactory) {
    this.sdkClient = sdkClient;
    this.channelFactory = channelFactory;
  }

  public void sendMessage(Message message) {
    try {
      sdkClient.sendMessage(message);
    } catch (A2AClientException e) {
      throw new ConnectorException(ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED, e);
    }
  }

  public Task getTask(TaskQueryParams request) {
    try {
      return sdkClient.getTask(request);
    } catch (A2AClientException e) {
      throw new ConnectorException(ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED, e);
    }
  }

  @Override
  public void close() {
    try {
      if (sdkClient != null) {
        sdkClient.close();
      }
    } catch (Throwable ignored) {
      // best-effort close; continue to shut down channels
    }

    try {
      if (channelFactory != null) {
        channelFactory.close();
      }
    } catch (Throwable ignored) {
      // best-effort cleanup
    }
  }
}
