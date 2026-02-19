/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;

public class AgentLimitsValidatorImpl implements AgentLimitsValidator {
  private static final int DEFAULT_MAX_MODEL_CALLS = 10;

  @Override
  public void validateConfiguredLimits(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    verifyMaxModelCalls(executionContext.limits(), agentContext);
  }

  private void verifyMaxModelCalls(LimitsConfiguration limits, AgentContext agentContext) {
    final int maxModelCalls =
        Optional.ofNullable(limits)
            .map(LimitsConfiguration::maxModelCalls)
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    if (agentContext.metrics().modelCalls() >= maxModelCalls) {
      throw new ConnectorException(
          ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED,
          "Maximum number of model calls reached (modelCalls: %d, limit: %d)"
              .formatted(agentContext.metrics().modelCalls(), maxModelCalls));
    }
  }
}
