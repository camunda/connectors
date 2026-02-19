/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * Validates the configured limits for an agent request.
 *
 * <p>Based on the configured limits, it can throw an exception if the limits are not met.
 */
public interface AgentLimitsValidator {
  void validateConfiguredLimits(AgentExecutionContext executionContext, AgentContext agentContext);
}
