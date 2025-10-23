/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.sdk.A2aClient;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRuntimeProperties;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
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

  private static final Logger LOG = LoggerFactory.getLogger(A2aPollingTask.class);

  private final InboundIntermediateConnectorContext context;
  private final ProcessInstanceContext processInstanceContext;
  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final ObjectMapper objectMapper;

  private A2aClient client;

  public A2aPollingTask(
      final InboundIntermediateConnectorContext context,
      final ProcessInstanceContext processInstanceContext,
      final A2aAgentCardFetcher agentCardFetcher,
      final A2aClientFactory clientFactory,
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

    switch (clientResponse) {
      case A2aMessage message -> handleMessage(message);
      case A2aTask task -> handleTask(runtimeProperties, task);
    }
  }

  private void handleMessage(final A2aMessage message) {
    LOG.debug("A2A message {} does not need polling -> directly correlating", message.messageId());
    processInstanceContext.correlate(message);
  }

  private void handleTask(final A2aPollingRuntimeProperties runtimeProperties, final A2aTask task) {
    if (context.canActivate(task) instanceof ActivationCheckResult.Success) {
      LOG.debug(
          "A2A task {} in state '{}' does not need polling -> directly correlating",
          task.id(),
          Optional.ofNullable(task.status())
              .map(A2aTaskStatus::state)
              .map(A2aTaskStatus.TaskState::asString)
              .orElse(null));
      processInstanceContext.correlate(task);
      return;
    }

    final var client = getClient(runtimeProperties);
    if (client == null) {
      return;
    }

    LOG.debug(
        "Polling A2A task {} with a max history length of {}",
        task.id(),
        runtimeProperties.data().historyLength());

    try {
      final var loadedTask =
          client.getTask(new TaskQueryParams(task.id(), runtimeProperties.data().historyLength()));
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

  private A2aPollingRuntimeProperties bindRuntimeProperties() {
    try {
      return processInstanceContext.bind(A2aPollingRuntimeProperties.class);
    } catch (Exception e) {
      LOG.error("Failed to bind A2A polling runtime properties", e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling-runtime-properties")
                  .withMessage("Failed to bind A2A polling runtime properties: " + e.getMessage()));
    }

    return null;
  }

  private A2aSendMessageResult getClientResponse(
      final A2aPollingRuntimeProperties runtimeProperties) {
    try {
      final var clientResponseJson = runtimeProperties.data().clientResponse();
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

  private synchronized A2aClient getClient(final A2aPollingRuntimeProperties runtimeProperties) {
    if (this.client == null) {
      try {
        final var agentCard =
            agentCardFetcher.fetchAgentCardRaw(runtimeProperties.data().connection());
        this.client =
            clientFactory.buildClient(
                agentCard, (event, ignore) -> {}, runtimeProperties.data().historyLength());
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

  @Override
  public void close() {
    if (this.client != null) {
      this.client.close();
    }
  }
}
