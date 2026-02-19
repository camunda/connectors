/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponse;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClient;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientConfigBuilder;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.model.A2aPollingRuntimeProperties;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import java.util.Objects;
import java.util.Optional;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aPollingTask.class);

  private final InboundIntermediateConnectorContext context;
  private final ProcessInstanceContext processInstanceContext;
  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final ObjectMapper objectMapper;

  private A2aSdkClient client;

  public A2aPollingTask(
      final InboundIntermediateConnectorContext context,
      final ProcessInstanceContext processInstanceContext,
      final A2aAgentCardFetcher agentCardFetcher,
      final A2aSdkClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter,
      final ObjectMapper objectMapper) {
    this.context = context;
    this.processInstanceContext = processInstanceContext;
    this.agentCardFetcher = agentCardFetcher;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run() {
    final var runtimeProperties = bindRuntimeProperties();
    if (runtimeProperties == null) {
      return;
    }

    final var clientResponse = getClientResponse(runtimeProperties);
    if (clientResponse == null) {
      return;
    }

    switch (clientResponse.result()) {
      case A2aAgentCard ignored -> handleAgentCard(clientResponse);
      case A2aMessage ignored -> handleMessage(clientResponse);
      case A2aTask ignored -> handleTask(runtimeProperties, clientResponse);
      default -> throw new IllegalStateException("Unexpected value: " + clientResponse);
    }
  }

  private void handleAgentCard(final A2aClientResponse clientResponse) {
    LOGGER.debug("A2A agent card result does not need polling -> directly correlating");
    processInstanceContext.correlate(clientResponse);
  }

  private void handleMessage(final A2aClientResponse clientResponse) {
    LOGGER.debug(
        "A2A message {} does not need polling -> directly correlating",
        Objects.requireNonNull(clientResponse.pollingData()).id());
    processInstanceContext.correlate(clientResponse);
  }

  private void handleTask(
      final A2aPollingRuntimeProperties runtimeProperties, final A2aClientResponse clientResponse) {
    final var task = (A2aTask) clientResponse.result();
    if (context.canActivate(clientResponse) instanceof ActivationCheckResult.Success) {
      LOGGER.debug(
          "A2A task {} in state '{}' does not need polling -> directly correlating",
          task.id(),
          Optional.ofNullable(task.status())
              .map(A2aTaskStatus::state)
              .map(A2aTaskStatus.TaskState::asString)
              .orElse(null));
      processInstanceContext.correlate(clientResponse);
      return;
    }

    final var client = getClient(runtimeProperties);
    if (client == null) {
      return;
    }

    LOGGER.debug(
        "Polling A2A task {} with a max history length of {}",
        task.id(),
        runtimeProperties.data().historyLength());

    try {
      final var loadedTask =
          client.getTask(new TaskQueryParams(task.id(), runtimeProperties.data().historyLength()));
      LOGGER.debug(
          "Loaded A2A task {} with state {}",
          task.id(),
          Optional.ofNullable(loadedTask.getStatus())
              .map(TaskStatus::state)
              .map(TaskState::asString)
              .orElse(null));

      final var convertedTask = objectConverter.convert(loadedTask);
      final var response =
          A2aClientResponse.builder()
              .result(convertedTask)
              .pollingData(clientResponse.pollingData())
              .build();
      processInstanceContext.correlate(response);
    } catch (Exception e) {
      LOGGER.error("Failed to poll A2A task {}", task.id(), e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling")
                  .withMessage(
                      "Failed to poll A2A task %s: %s".formatted(task.id(), e.getMessage())));
    }
  }

  private A2aPollingRuntimeProperties bindRuntimeProperties() {
    try {
      return processInstanceContext.bind(A2aPollingRuntimeProperties.class);
    } catch (Exception e) {
      LOGGER.error("Failed to bind A2A polling runtime properties", e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling-runtime-properties")
                  .withMessage("Failed to bind A2A polling runtime properties: " + e.getMessage()));
    }

    return null;
  }

  private A2aClientResponse getClientResponse(final A2aPollingRuntimeProperties runtimeProperties) {
    try {
      final var clientResponseJson = runtimeProperties.data().clientResponse();
      return objectMapper.readValue(clientResponseJson, A2aClientResponse.class);
    } catch (Exception e) {
      LOGGER.debug("Failed to load A2A client response", e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling-response")
                  .withMessage("Error loading A2A client response: " + e.getMessage()));
    }

    return null;
  }

  private synchronized A2aSdkClient getClient(final A2aPollingRuntimeProperties runtimeProperties) {
    if (this.client == null) {
      try {
        final var agentCard =
            agentCardFetcher.fetchAgentCardRaw(runtimeProperties.data().connection());
        final var a2aClientConfig =
            A2aSdkClientConfigBuilder.builder()
                .historyLength(runtimeProperties.data().historyLength())
                .build();
        this.client = clientFactory.buildClient(agentCard, (event, ignore) -> {}, a2aClientConfig);
      } catch (Exception e) {
        LOGGER.error("Failed to create A2A client", e);
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

  @Override
  public void close() {
    if (this.client != null) {
      this.client.close();
    }
  }
}
