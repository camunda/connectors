/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public class A2aMessageSenderImpl implements A2aMessageSender {

  private final A2aDocumentToPartConverter documentToPartConverter;
  private final A2aSendMessageResponseHandler sendMessageResponseHandler;
  private final A2aSdkClientFactory clientFactory;

  public A2aMessageSenderImpl(
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      A2aSdkClientFactory clientFactory) {
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

    Client client =
        clientFactory.buildClient(
            agentCard, consumer, sendMessageOperation.settings().historyLength());
    try {
      try {
        client.sendMessage(message);
      } catch (A2AClientException e) {
        throw new RuntimeException(e);
      }

    try {
      return response.get(
          sendMessageOperation.settings().timeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // TODO: should be a ConnectorException with a specific error code?
      throw new RuntimeException("Timed out waiting for response from agent.", e);
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
    } finally {
      clientFactory.release(client);
    }
  }

  private Message createMessage(SendMessageOperationConfiguration sendMessageOperation) {
    List<Part<?>> parts = new ArrayList<>();
    parts.add(new TextPart(sendMessageOperation.params().text()));
    parts.addAll(documentToPartConverter.convert(sendMessageOperation.params().documents()));
    return new Message.Builder().role(Message.Role.USER).parts(parts).build();
  }
}
