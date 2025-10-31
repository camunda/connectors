/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import io.a2a.client.ClientEvent;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.model.A2aSendMessageOperationParameters;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.sdk.A2aClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class A2aMessageSenderImpl implements A2aMessageSender {
  private final A2aDocumentToPartConverter documentToPartConverter;
  private final A2aSendMessageResponseHandler sendMessageResponseHandler;
  private final A2aClientFactory clientFactory;

  public A2aMessageSenderImpl(
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      A2aClientFactory clientFactory) {
    this.documentToPartConverter = documentToPartConverter;
    this.sendMessageResponseHandler = sendMessageResponseHandler;
    this.clientFactory = clientFactory;
  }

  @Override
  public A2aSendMessageResult sendMessage(
      AgentCard agentCard, SendMessageOperationConfiguration sendMessageOperation) {
    Message message = createMessage(sendMessageOperation);
    CompletableFuture<A2aSendMessageResult> response = new CompletableFuture<>();
    BiConsumer<ClientEvent, AgentCard> consumer =
        (event, ignore) -> {
          try {
            A2aSendMessageResult result = sendMessageResponseHandler.handleClientEvent(event);
            response.complete(result);
          } catch (Exception e) {
            response.completeExceptionally(e);
          }
        };
    try (var a2aClient =
        clientFactory.buildClient(
            agentCard, consumer, A2aClientConfig.from(sendMessageOperation.settings()))) {
      a2aClient.sendMessage(message);

      try {
        return response.get(
            sendMessageOperation.settings().timeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        // TODO: should be a ConnectorException with a specific error code?
        throw new RuntimeException("Timed out waiting for response from agent.", e);
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
      }
    }
  }

  private Message createMessage(SendMessageOperationConfiguration sendMessageOperation) {
    List<Part<?>> parts = new ArrayList<>();
    A2aSendMessageOperationParameters parameters = sendMessageOperation.params();
    parts.add(new TextPart(parameters.text()));
    parts.addAll(documentToPartConverter.convert(parameters.documents()));

    Message.Builder builder = new Message.Builder().role(Message.Role.USER).parts(parts);

    if (StringUtils.isNotBlank(parameters.contextId())) {
      builder.contextId(parameters.contextId());
    }

    if (StringUtils.isNotBlank(parameters.taskId())) {
      builder.taskId(parameters.taskId());
    }

    if (CollectionUtils.isNotEmpty(parameters.referenceTaskIds())) {
      builder.referenceTaskIds(parameters.referenceTaskIds());
    }

    return builder.build();
  }
}
