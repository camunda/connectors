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
import io.a2a.spec.AgentCard;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingElementInstanceRequest;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollA2aTaskStateTask implements Runnable, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PollA2aTaskStateTask.class);

  private final InboundIntermediateConnectorContext context;
  private final ProcessInstanceContext processInstanceContext;
  private final A2aPollingRequest pollingRequest;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;

  private String taskId;
  private AgentCard agentCard;
  private Client client;

  public PollA2aTaskStateTask(
      final InboundIntermediateConnectorContext context,
      final ProcessInstanceContext processInstanceContext,
      final A2aPollingRequest pollingRequest,
      final A2aSdkClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter) {
    this.context = context;
    this.processInstanceContext = processInstanceContext;
    this.pollingRequest = pollingRequest;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
  }

  @Override
  public void run() {
    final var taskId = getTaskId();
    if (taskId == null) {
      return;
    }

    final var client = getClient();
    if (client == null) {
      return;
    }

    LOG.debug("Polling A2A task {}", taskId);

    try {
      final var task = client.getTask(new TaskQueryParams(taskId));
      LOG.debug(
          "A2A task {} state: {}",
          taskId,
          Optional.ofNullable(task.getStatus())
              .map(TaskStatus::state)
              .map(TaskState::asString)
              .orElse(null));

      final var convertedTask = objectConverter.convert(task);
      processInstanceContext.correlate(convertedTask);
    } catch (Exception e) {
      LOG.error("Failed to poll A2A task %s".formatted(taskId), e);
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-polling")
                  .withMessage("Failed to poll A2A task %s: %s".formatted(taskId, e.getMessage())));
    }
  }

  private synchronized String getTaskId() {
    if (this.taskId == null) {
      try {
        // resolves the actual task ID from process variables
        final var elementInstanceRequest =
            processInstanceContext.bind(A2aPollingElementInstanceRequest.class);
        this.taskId = elementInstanceRequest.data().taskId();
      } catch (Exception e) {
        LOG.debug("Failed to resolve A2A Task ID", e);
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag("a2a-polling-config")
                    .withMessage("Error resolving A2A task ID: " + e.getMessage()));
      }
    }

    return this.taskId;
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
