/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingElementInstanceRequest;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task responsible for polling status for a specific A2A response for a given process instance.
 * Started and managed from {@link A2aPollingProcessInstancesFetcherTask}
 *
 * <p>If the response is a message or an already completed/failed task, it will be directly
 * correlated without further polling.
 */
public class A2aPollingTask implements Runnable, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(A2aPollingTask.class);
  private static final List<A2aTaskStatus.TaskState> POLLABLE_TASK_STATES =
      List.of(A2aTaskStatus.TaskState.SUBMITTED, A2aTaskStatus.TaskState.WORKING);

  private final InboundIntermediateConnectorContext context;
  private final ProcessInstanceContext processInstanceContext;
  private final A2aPollingRequest pollingRequest;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final ObjectMapper objectMapper;

  private AgentCard agentCard;
  private Client client;

  public A2aPollingTask(
      final InboundIntermediateConnectorContext context,
      final ProcessInstanceContext processInstanceContext,
      final A2aPollingRequest pollingRequest,
      final A2aSdkClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter,
      final ObjectMapper objectMapper) {
    this.context = context;
    this.processInstanceContext = processInstanceContext;
    this.pollingRequest = pollingRequest;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run() {
    final var clientResponse = getClientResponse();
    if (clientResponse == null) {
      return;
    }

    switch (clientResponse) {
      case A2aMessage message -> handleMessage(message);
      case A2aTask task -> handleTask(task);
    }
  }

  private void handleMessage(final A2aMessage message) {
    LOG.debug("A2A message {} does not need polling -> directly correlating", message.messageId());
    processInstanceContext.correlate(message);
  }

  private void handleTask(final A2aTask task) {
    final var taskState = Optional.ofNullable(task.status()).map(A2aTaskStatus::state).orElse(null);
    final var needsPolling = taskState == null || POLLABLE_TASK_STATES.contains(taskState);
    if (!needsPolling) {
      LOG.debug(
          "A2A task {} in state '{}' does not need polling -> directly correlating",
          task.id(),
          taskState.asString());
      processInstanceContext.correlate(task);
      return;
    }

    final var client = getClient();
    if (client == null) {
      return;
    }

    LOG.debug(
        "Polling A2A task {} with a max history length of {}",
        task.id(),
        pollingRequest.data().historyLength());

    try {
      final var loadedTask =
          client.getTask(new TaskQueryParams(task.id(), pollingRequest.data().historyLength()));
      LOG.debug(
          "Loaded A2A task {} with state {}",
          task.id(),
          Optional.ofNullable(loadedTask.getStatus())
              .map(TaskStatus::state)
              .map(TaskState::asString)
              .orElse(null));

      final var convertedTask = objectConverter.convert(loadedTask);
      processInstanceContext.correlate(convertedTask);
    } catch (Exception e) {
      LOG.error("Failed to poll A2A task %s".formatted(task.id()), e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling")
                  .withMessage(
                      "Failed to poll A2A task %s: %s".formatted(task.id(), e.getMessage())));
    }
  }

  private A2aSendMessageResult getClientResponse() {
    try {
      final var elementInstanceRequest =
          processInstanceContext.bind(A2aPollingElementInstanceRequest.class);
      final var clientResponseJson = elementInstanceRequest.data().clientResponse();
      return objectMapper.readValue(clientResponseJson, A2aSendMessageResult.class);
    } catch (Exception e) {
      LOG.debug("Failed to load A2A client response", e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling-response")
                  .withMessage("Error loading A2A client response: " + e.getMessage()));
    }

    return null;
  }

  private synchronized Client getClient() {
    if (this.client == null) {
      try {
        final var agentCard = getAgentCard();
        if (agentCard == null) {
          return null;
        }

        this.client = clientFactory.buildClient(agentCard, (event, ignore) -> {});
      } catch (Exception e) {
        LOG.error("Failed to create A2A client", e);
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag("a2a-polling-client")
                    .withMessage("Failed to create A2A client: " + e.getMessage()));
      }
    }

    return this.client;
  }

  private synchronized AgentCard getAgentCard() {
    if (this.agentCard == null) {
      try {
        final var connection = pollingRequest.data().connection();
        this.agentCard =
            A2A.getAgentCard(
                connection.url(),
                Optional.ofNullable(connection.agentCardLocation())
                    .filter(StringUtils::isNotBlank)
                    .orElse(null),
                Map.of());
      } catch (A2AClientError e) {
        LOG.error("Failed to load A2A Agent Card", e);
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag("a2a-polling-agent-card")
                    .withMessage("Failed to load A2A Agent Card: " + e.getMessage()));
      }
    }

    return this.agentCard;
  }

  @Override
  public void close() {
    if (this.client != null) {
      this.client.close();
    }
  }
}
