/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceErrorClassifier.Decision;
import io.camunda.connector.agenticai.util.retry.ExponentialBackoffRetry;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaAgentInstanceClient implements AgentInstanceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaAgentInstanceClient.class);

  private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
  private static final int MAX_RETRIES = 4; // 5 total attempts

  private final CamundaClient camundaClient;

  public CamundaAgentInstanceClient(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public long create(CreateAgentInstanceParams params) {
    final int maxAttempts = MAX_RETRIES + 1;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return executeCreate(params);
      } catch (Exception e) {
        final Decision decision = AgentInstanceErrorClassifier.classify(e);
        if (!decision.isRetryable() || areRetriesExhausted(attempt)) {
          throw buildException(params, e, decision, attempt);
        }
        scheduleRetry(params, e, attempt);
      }
    }
    throw new IllegalStateException("Unexpected end of retry loop");
  }

  private static boolean areRetriesExhausted(int attempt) {
    return attempt == 5;
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
      CreateAgentInstanceParams params, Exception e, Decision decision, int attempt) {
    final String message =
        (decision == Decision.PERMANENT)
            ? "Failed to create agent instance for element instance key %d: %s"
                .formatted(params.elementInstanceKey(), e.getMessage())
            : "Failed to create agent instance for element instance key %d after %d attempt(s): %s"
                .formatted(params.elementInstanceKey(), attempt, e.getMessage());
    return new ConnectorException(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED, message, e);
  }

  private void scheduleRetry(CreateAgentInstanceParams params, Exception e, int attempt) {
    final Duration delay =
        ExponentialBackoffRetry.delayBeforeAttempt(attempt + 1, INITIAL_RETRY_DELAY);
    LOGGER.warn(
        "Attempt {}/{} to create agent instance for element instance key {} failed, retrying in {}ms: {}",
        attempt,
        CamundaAgentInstanceClient.MAX_RETRIES + 1,
        params.elementInstanceKey(),
        delay.toMillis(),
        e.getMessage());
    try {
      sleep(delay);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ConnectorException(
          ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED,
          "Interrupted while waiting to retry agent instance creation for element instance key %d"
              .formatted(params.elementInstanceKey()),
          ie);
    }
  }

  protected void sleep(Duration delay) throws InterruptedException {
    Thread.sleep(delay.toMillis());
  }
}
