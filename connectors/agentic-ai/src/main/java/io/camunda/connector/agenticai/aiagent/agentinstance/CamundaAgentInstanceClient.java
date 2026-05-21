/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private final CamundaClient camundaClient;
  private final AgenticAiConnectorsConfigurationProperties.RetriesProperties retriesProperties;

  public CamundaAgentInstanceClient(
      CamundaClient camundaClient,
      AgenticAiConnectorsConfigurationProperties.RetriesProperties retriesProperties) {
    this.camundaClient = camundaClient;
    this.retriesProperties = retriesProperties;
  }

  @Override
  public AgentInstanceKey create(AgentExecutionContext agentExecutionContext) {
    return CamundaApiRetry.execute(
        () -> executeCreate(agentExecutionContext),
        AgentInstanceErrorClassifier.INSTANCE,
        retriesProperties.maxRetries(),
        retriesProperties.initialRetryDelay(),
        (cause, attempt, reason) -> buildException(agentExecutionContext, cause, attempt, reason),
        this::sleep);
  }

  private AgentInstanceKey executeCreate(AgentExecutionContext agentExecutionContext) {
    var command =
        camundaClient
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(agentExecutionContext.jobContext().getElementInstanceKey())
            .provider(agentExecutionContext.provider().providerType())
            .model(agentExecutionContext.provider().model())
            .systemPrompt(agentExecutionContext.systemPrompt().prompt());

    final var limits = agentExecutionContext.limits();
    if (limits != null && limits.maxModelCalls() != null) {
      command = command.maxModelCalls(limits.maxModelCalls());
    }
    return AgentInstanceKey.of(command.execute().getAgentInstanceKey());
  }

  private ConnectorException buildException(
      AgentExecutionContext agentExecutionContext,
      Throwable cause,
      int attempt,
      FailureReason reason) {
    final long elementInstanceKey = agentExecutionContext.jobContext().getElementInstanceKey();
    final String message =
        switch (reason) {
          case PERMANENT_ERROR ->
              "Failed to create agent instance for element instance key %d: %s"
                  .formatted(elementInstanceKey, cause.getMessage());
          case RETRIES_EXHAUSTED ->
              "Failed to create agent instance for element instance key %d after %d attempt(s): %s"
                  .formatted(elementInstanceKey, attempt, cause.getMessage());
          case INTERRUPTED ->
              "Interrupted while waiting to retry agent instance creation for element instance key %d"
                  .formatted(elementInstanceKey);
        };
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, message, cause);
  }

  protected void sleep(Duration delay) throws InterruptedException {
    Thread.sleep(delay.toMillis());
  }
}
