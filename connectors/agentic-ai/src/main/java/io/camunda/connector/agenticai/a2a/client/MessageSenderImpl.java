/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.convert.DocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.model.A2aClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import org.apache.commons.collections4.CollectionUtils;

public class MessageSenderImpl implements MessageSender {

  private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

  private final DocumentToPartConverter documentToPartConverter;
  private final SendMessageResponseHandler sendMessageResponseHandler;
  private final TaskPoller taskPoller;
  private final ClientFactory clientFactory;

  public MessageSenderImpl(
      DocumentToPartConverter documentToPartConverter,
      SendMessageResponseHandler sendMessageResponseHandler,
      TaskPoller taskPoller,
      ClientFactory clientFactory) {
    this.documentToPartConverter = documentToPartConverter;
    this.sendMessageResponseHandler = sendMessageResponseHandler;
    this.taskPoller = taskPoller;
    this.clientFactory = clientFactory;
  }

  @Override
  public A2aSendMessageResult sendMessage(
      SendMessageOperationConfiguration sendMessageOperation, AgentCard agentCard) {
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

    Client client = clientFactory.buildClient(agentCard, consumer);
    try {
      client.sendMessage(message);
    } catch (A2AClientException e) {
      throw new RuntimeException(e);
    }

    try {
      Instant startTime = Instant.now();
      A2aSendMessageResult result =
          response.get(sendMessageOperation.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (result instanceof A2aSendMessageResult.A2aTaskResult(A2aTask task)
          && task.status().state().isSubmittedOrWorking()) {
        Duration timeSpent = Duration.between(startTime, Instant.now());
        Duration updatedTimeout = sendMessageOperation.timeout().minus(timeSpent);
        return taskPoller
            .poll(task.taskId(), client, DEFAULT_POLL_INTERVAL, updatedTimeout)
            .get(updatedTimeout.toMillis(), TimeUnit.MILLISECONDS);
      }
      return result;
    } catch (TimeoutException e) {
      // TODO: should be a ConnectorException with a specific error code?
      throw new RuntimeException("Timed out waiting for response from agent.", e);
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
    } finally {
      client.close();
    }
  }

  private Message createMessage(SendMessageOperationConfiguration sendMessageOperation) {
    List<Part<?>> parts = new ArrayList<>();
    parts.add(new TextPart(sendMessageOperation.params().text()));
    if (CollectionUtils.isNotEmpty(sendMessageOperation.params().documents())) {
      for (var document : sendMessageOperation.params().documents()) {
        parts.add(documentToPartConverter.convert(document));
      }
    }

    return new Message.Builder().role(Message.Role.USER).parts(parts).build();
  }
}
