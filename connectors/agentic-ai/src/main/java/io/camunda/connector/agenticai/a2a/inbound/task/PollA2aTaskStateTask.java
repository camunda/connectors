/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.task;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aProcessInstanceRequest;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollA2aTaskStateTask implements Runnable, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PollA2aTaskStateTask.class.getName());

  private final InboundIntermediateConnectorContext context;
  private final ProcessInstanceContext processInstanceContext;
  private final A2aPollingRequest pollingRequest;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private AgentCard agentCard = null;
  private Client client;

  public PollA2aTaskStateTask(
      final InboundIntermediateConnectorContext context,
      final ProcessInstanceContext processInstanceContext,
      final A2aPollingRequest pollingRequest,
      final A2aSdkClientFactory clientFactory,
      A2aSdkObjectConverter objectConverter) {
    this.context = context;
    this.processInstanceContext = processInstanceContext;
    this.pollingRequest = pollingRequest;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
  }

  @Override
  public void run() {
    // process instance configuration with resolved task ID from process variables (binds FEEL
    // expression to process variable data)
    final var processInstanceConfig = processInstanceContext.bind(A2aProcessInstanceRequest.class);
    LOG.info("Polling A2A task {} status...", processInstanceConfig.data().taskId());

    final var client = getClient();
    if (client == null) {
      return;
    }

    try {
      final var task = client.getTask(new TaskQueryParams(processInstanceConfig.data().taskId()));
      LOG.info("TASK STATUS: {}", task.getStatus());

      if (canCorrelate(task)) {
        final var convertedTask = objectConverter.convert(task);
        processInstanceContext.correlate(convertedTask);
      }
    } catch (A2AClientException e) {
      LOG.error("Failed to poll A2A task status", e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling")
                  .withMessage("Failed to poll A2A task status: " + e.getMessage()));
    }
  }

  @Override
  public void close() {
    if (this.client != null) {
      this.client.close();
    }
  }

  private boolean canCorrelate(Task task) {
    final var state = task.getStatus().state();
    return switch (state) {
      case SUBMITTED, WORKING:
        LOG.info("Task is still in progress...");
        yield false;
      case AUTH_REQUIRED:
        LOG.warn("Task requires authorization");
        yield true;
      case INPUT_REQUIRED:
        LOG.warn("Task requires input");
        yield true;
      default:
        if (state.isFinal()) {
          yield true;
        } else {
          LOG.error("Unhandled non-final task state: {}", state);
          throw new RuntimeException("Unhandled non-final task state: " + state);
        }
    };
  }

  private synchronized Client getClient() {
    if (this.client == null) {
      final var agentCard = getAgentCard();
      if (agentCard == null) {
        return null;
      }

      this.client = clientFactory.buildClient(agentCard, (event, ignore) -> {});
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
}
