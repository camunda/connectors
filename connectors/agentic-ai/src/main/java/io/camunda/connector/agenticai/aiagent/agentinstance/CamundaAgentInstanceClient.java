/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry;
import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
  private static final int MAX_RETRIES = 4; // 5 total attempts

  private final CamundaClient camundaClient;

  public CamundaAgentInstanceClient(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public long create(CreateAgentInstanceParams params) {
    return CamundaApiRetry.execute(
        () -> executeCreate(params),
        AgentInstanceErrorClassifier::classify,
        MAX_RETRIES,
        INITIAL_RETRY_DELAY,
        (cause, attempt, reason) -> buildException(params, cause, attempt, reason),
        this::sleep);
  }

  private long executeCreate(CreateAgentInstanceParams params) {
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
    var result = command.execute();

    return result.getAgentInstanceKey();
  }

  private ConnectorException buildException(
      CreateAgentInstanceParams params, Throwable cause, int attempt, FailureReason reason) {
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
