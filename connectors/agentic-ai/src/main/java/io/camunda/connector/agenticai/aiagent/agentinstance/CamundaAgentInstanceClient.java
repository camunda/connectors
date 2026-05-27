/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.RetriesProperties;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.api.error.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaAgentInstanceClient.class);

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;
  private final Sleeper sleeper;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient, RetriesProperties retriesProperties, Sleeper sleeper) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
    this.sleeper = sleeper;
  }

  @Override
  public AgentInstanceKey create(AgentExecutionContext agentExecutionContext) {
    return CamundaApiRetry.execute(
        () -> executeCreate(agentExecutionContext),
        AgentInstanceErrorClassifier.FOR_CREATE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildCreateException,
        sleeper);
  }

  private AgentInstanceKey executeCreate(AgentExecutionContext agentExecutionContext) {
    final long elementInstanceKey = agentExecutionContext.jobContext().getElementInstanceKey();
    LOGGER.debug(
        "Creating agent instance for element instance {}: model={}, provider={}",
        elementInstanceKey,
        agentExecutionContext.provider().model(),
        agentExecutionContext.provider().providerType());

    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model(agentExecutionContext.provider().model())
            .provider(agentExecutionContext.provider().providerType())
            .systemPrompt(agentExecutionContext.systemPrompt().prompt());

    final var limits = agentExecutionContext.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }
    final var key = AgentInstanceKey.of(command.execute().getAgentInstanceKey());
    LOGGER.debug(
        "Created agent instance {} for element instance {}", key.value(), elementInstanceKey);
    return key;
  }

  @Override
  public void update(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      AgentInstanceUpdateRequest request) {
    final var metadata = agentContext.metadata();
    if (metadata == null || metadata.agentInstanceKey() == null) {
      LOGGER.debug("Skipping agent instance update: no agent instance key in context");
      return;
    }
    CamundaApiRetry.execute(
        () -> executeUpdate(executionContext, metadata.agentInstanceKey(), request),
        AgentInstanceErrorClassifier.FOR_UPDATE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        this::buildUpdateException,
        sleeper);
  }

  private Void executeUpdate(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentInstanceUpdateRequest request) {
    LOGGER.debug(
        "Updating agent instance {}: status={}, delta={}",
        agentInstanceKey,
        request.status(),
        request.delta());
    UpdateAgentInstanceCommandStep2 cmd =
        camundaClient
            .newUpdateAgentInstanceCommand(agentInstanceKey)
            .elementInstanceKey(executionContext.jobContext().getElementInstanceKey());

    if (request.status() != null) {
      cmd = cmd.status(request.status());
    }

    final var delta = request.delta();
    if (delta != null) {
      if (delta.modelCalls() != 0) {
        cmd = cmd.modelCalls(delta.modelCalls());
      }
      if (delta.tokenUsage().inputTokenCount() != 0) {
        cmd = cmd.inputTokens(delta.tokenUsage().inputTokenCount());
      }
      if (delta.tokenUsage().outputTokenCount() != 0) {
        cmd = cmd.outputTokens(delta.tokenUsage().outputTokenCount());
      }
      if (delta.toolCalls() != 0) {
        cmd = cmd.toolCalls(delta.toolCalls());
      }
    }

    cmd.execute();
    return null;
  }

  private ConnectorException buildCreateException(
      Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to create agent instance: %s".formatted(cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to create agent instance after %d attempt(s): %s"
                  .formatted(attempt, cause.getMessage());
          case INTERRUPTED -> "Interrupted while waiting to retry agent instance creation";
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, message, cause);
  }

  private ConnectorException buildUpdateException(
      Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to update agent instance: %s".formatted(cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to update agent instance after %d attempt(s): %s"
                  .formatted(attempt, cause.getMessage());
          case INTERRUPTED -> "Interrupted while waiting to retry agent instance update";
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED, message, cause);
  }
}
