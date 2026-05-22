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

public class CamundaAgentInstanceClient implements AgentInstanceClient {

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
        (cause, attempt, reason) -> buildCreateException(cause, attempt, reason),
        sleeper);
  }

  private AgentInstanceKey executeCreate(AgentExecutionContext agentExecutionContext) {
    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(agentExecutionContext.jobContext().getElementInstanceKey())
            .model(agentExecutionContext.provider().model())
            .provider(agentExecutionContext.provider().providerType())
            .systemPrompt(agentExecutionContext.systemPrompt().prompt());

    final var limits = agentExecutionContext.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }
    return AgentInstanceKey.of(command.execute().getAgentInstanceKey());
  }

  @Override
  public void update(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      AgentInstanceUpdateRequest request) {
    final var metadata = agentContext.metadata();
    if (metadata == null || metadata.agentInstanceKey() == null) {
      return;
    }
    CamundaApiRetry.execute(
        () -> executeUpdate(executionContext, metadata.agentInstanceKey(), request),
        AgentInstanceErrorClassifier.FOR_UPDATE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        (cause, attempt, reason) -> buildUpdateException(cause, attempt, reason),
        sleeper);
  }

  private Void executeUpdate(
      AgentExecutionContext executionContext,
      long agentInstanceKey,
      AgentInstanceUpdateRequest request) {
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
        cmd = cmd.inputTokens((long) delta.tokenUsage().inputTokenCount());
      }
      if (delta.tokenUsage().outputTokenCount() != 0) {
        cmd = cmd.outputTokens((long) delta.tokenUsage().outputTokenCount());
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
