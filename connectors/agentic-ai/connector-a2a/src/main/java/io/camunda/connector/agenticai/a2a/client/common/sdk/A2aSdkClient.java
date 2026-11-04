/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED;
import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class A2aSdkClient implements AutoCloseable {
  private final Client sdkClient;
  private final ManagedChannelFactory channelFactory;
  private final A2aDocumentToPartConverter documentToPartConverter;
  private final A2aSdkObjectConverter sdkObjectConverter;

  public A2aSdkClient(
      Client sdkClient,
      ManagedChannelFactory channelFactory,
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSdkObjectConverter sdkObjectConverter) {
    this.sdkClient = sdkClient;
    this.channelFactory = channelFactory;
    this.documentToPartConverter = documentToPartConverter;
    this.sdkObjectConverter = sdkObjectConverter;
  }

  public void sendMessage(A2aOutgoingMessage outgoingMessage) {
    final var message = createMessage(outgoingMessage);
    try {
      sdkClient.sendMessage(message);
    } catch (A2AClientException e) {
      throw new ConnectorException(ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED, e);
    }
  }

  public A2aTask getTask(String taskId, int historyLength) {
    try {
      return sdkObjectConverter.convert(
          sdkClient.getTask(new TaskQueryParams(taskId, historyLength)));
    } catch (A2AClientException e) {
      throw new ConnectorException(ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED, e);
    }
  }

  private Message createMessage(A2aOutgoingMessage outgoingMessage) {
    List<Part<?>> parts = new ArrayList<>();
    parts.add(new TextPart(outgoingMessage.text(), null));
    if (outgoingMessage.documents() != null) {
      parts.addAll(documentToPartConverter.convert(outgoingMessage.documents()));
    }

    Message.Builder builder =
        Message.builder()
            .role(Message.Role.ROLE_USER)
            .messageId(UUID.randomUUID().toString())
            .parts(parts);

    if (StringUtils.isNotBlank(outgoingMessage.contextId())) {
      builder.contextId(outgoingMessage.contextId());
    }

    if (StringUtils.isNotBlank(outgoingMessage.taskId())) {
      builder.taskId(outgoingMessage.taskId());
    }

    if (CollectionUtils.isNotEmpty(outgoingMessage.referenceTaskIds())) {
      builder.referenceTaskIds(outgoingMessage.referenceTaskIds());
    }

    return builder.build();
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
