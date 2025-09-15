/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.http.JdkHttpClient;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.OperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.OperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientAgentCardResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import org.apache.commons.collections4.CollectionUtils;

// TODO: proper exception handling
public class A2AClientHandlerImpl implements A2AClientHandler {

  private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

  private final DocumentToPartConverter documentToPartConverter;
  private final SendMessageResultHandler sendMessageResultHandler;
  private final TaskPoller taskPoller;

  public A2AClientHandlerImpl(
      DocumentToPartConverter documentToPartConverter,
      SendMessageResultHandler sendMessageResultHandler,
      TaskPoller taskPoller) {
    this.documentToPartConverter = documentToPartConverter;
    this.sendMessageResultHandler = sendMessageResultHandler;
    this.taskPoller = taskPoller;
  }

  @Override
  public A2AClientResult handle(A2AClientRequest request) {
    final var connection = request.data().connection();
    return switch (request.data().operation()) {
      case FetchAgentCardOperationConfiguration ignored -> fetchAgentCard(connection);
      case SendMessageOperationConfiguration sendMessageOperation ->
          sendMessage(sendMessageOperation, connection);
    };
  }

  private A2AClientAgentCardResult fetchAgentCard(ConnectionConfiguration connection) {
    AgentCard agentCard = fetchAgentCardRaw(connection);
    return convertAgentCard(agentCard);
  }

  private A2AClientSendMessageResult sendMessage(
      SendMessageOperationConfiguration sendMessageOperation, ConnectionConfiguration connection) {
    Message message = createMessage(sendMessageOperation);
    CompletableFuture<A2AClientSendMessageResult> response = new CompletableFuture<>();
    BiConsumer<ClientEvent, AgentCard> consumer =
        (event, ignore) -> {
          try {
            A2AClientSendMessageResult result = sendMessageResultHandler.handleClientEvent(event);
            response.complete(result);
          } catch (Exception e) {
            response.completeExceptionally(e);
          }
        };

    AgentCard agentCard = fetchAgentCardRaw(connection); // TODO: cache agent card
    Client client = buildClient(agentCard, consumer);
    try {
      client.sendMessage(message);
    } catch (A2AClientException e) {
      throw new RuntimeException(e);
    }

    try {
      Instant startTime = Instant.now();
      A2AClientSendMessageResult result =
          response.get(sendMessageOperation.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (result.state().isSubmittedOrWorking()) {
        Duration timeSpent = Duration.between(startTime, Instant.now());
        Duration updatedTimeout =
            sendMessageOperation.timeout().minus(timeSpent); // TODO: check for negative duration
        return taskPoller
            .poll(result.responseId(), client, DEFAULT_POLL_INTERVAL, updatedTimeout)
            .get(updatedTimeout.toMillis(), TimeUnit.MILLISECONDS);
      }
      return result;
    } catch (TimeoutException e) {
      throw new RuntimeException("Timed out waiting for response from agent.", e);
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    } finally {
      client.close();
    }
  }

  private Message createMessage(SendMessageOperationConfiguration sendMessageOperation) {
    List<Part<?>> parts = new ArrayList<>();
    parts.add(new TextPart(sendMessageOperation.text()));
    if (CollectionUtils.isNotEmpty(sendMessageOperation.documents())) {
      for (var document : sendMessageOperation.documents()) {
        parts.add(documentToPartConverter.convert(document));
      }
    }

    return new Message.Builder().role(Message.Role.USER).parts(parts).build();
  }

  private Client buildClient(AgentCard agentCard, BiConsumer<ClientEvent, AgentCard> consumer) {
    try {
      // Disable streaming for the time being
      return Client.builder(agentCard)
          .clientConfig(new ClientConfig.Builder().setStreaming(false).setPolling(true).build())
          .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig(new JdkHttpClient()))
          .addConsumer(consumer)
          .build();
    } catch (A2AClientException e) {
      throw new RuntimeException(e);
    }
  }

  private AgentCard fetchAgentCardRaw(ConnectionConfiguration connection) {
    final var relativeCardPath =
        isNotBlank(connection.agentCardLocation()) ? connection.agentCardLocation() : null;
    try {
      return A2A.getAgentCard(connection.url(), relativeCardPath, Collections.emptyMap());
    } catch (A2AClientError e) {
      throw new RuntimeException(e);
    }
  }

  private A2AClientAgentCardResult convertAgentCard(AgentCard agentCard) {
    final var agentSkills =
        agentCard.skills().stream()
            .map(
                agentSkill ->
                    new A2AClientAgentCardResult.AgentSkill(
                        agentSkill.id(),
                        agentSkill.name(),
                        agentSkill.description(),
                        agentSkill.tags(),
                        agentSkill.examples(),
                        CollectionUtils.isEmpty(agentSkill.inputModes())
                            ? agentCard.defaultInputModes()
                            : agentSkill.inputModes(),
                        CollectionUtils.isEmpty(agentSkill.outputModes())
                            ? agentCard.defaultOutputModes()
                            : agentSkill.outputModes()))
            .toList();
    return new A2AClientAgentCardResult(agentCard.name(), agentCard.description(), agentSkills);
  }
}
