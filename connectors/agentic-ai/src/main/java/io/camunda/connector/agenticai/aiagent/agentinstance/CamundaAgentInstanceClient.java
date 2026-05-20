/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.AiAgentProperties.AgentInstanceProperties.RetriesProperties;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private final CamundaClient camundaClient;
  private final RetriesProperties retriesProperties;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient, RetriesProperties retriesProperties) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
  }

  @Override
  public AgentInstanceKey create(InitialAgentInstanceData params) {
    return CamundaApiRetry.execute(
        () -> executeCreate(params),
        AgentInstanceErrorClassifier::classify,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        (cause, attempt, reason) -> buildException(params, cause, attempt, reason),
        this::sleep);
  }

  private AgentInstanceKey executeCreate(InitialAgentInstanceData params) {
    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(params.elementInstanceKey())
            .model(params.model())
            .provider(params.provider())
            .systemPrompt(params.systemPrompt());
    final var limits = params.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }
    return AgentInstanceKey.of(command.execute().getAgentInstanceKey());
  }

  private ConnectorException buildException(
      InitialAgentInstanceData params, Throwable cause, int attempt, FailureReason reason) {
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to create agent instance for element instance key %d: %s"
                  .formatted(params.elementInstanceKey(), cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to create agent instance for element instance key %d after %d attempt(s): %s"
                  .formatted(params.elementInstanceKey(), attempt, cause.getMessage());
          case INTERRUPTED ->
              "Interrupted while waiting to retry agent instance creation for element instance key %d"
                  .formatted(params.elementInstanceKey());
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, message, cause);
  }

  protected void sleep(Duration delay) throws InterruptedException {
    Thread.sleep(delay.toMillis());
  }
}
