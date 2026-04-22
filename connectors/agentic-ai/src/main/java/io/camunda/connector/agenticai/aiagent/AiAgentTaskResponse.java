/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.StandardConnectorResponse;
import org.springframework.lang.Nullable;

/**
 * Response type for the AI Agent task flavor (outbound connector on a service task). Wraps the
 * {@link AgentResponse} as a {@link StandardConnectorResponse} so the runtime evaluates it via
 * result expressions.
 */
public record AiAgentTaskResponse(@Nullable AgentResponse agentResponse)
    implements StandardConnectorResponse {

  @Override
  public Object responseValue() {
    return agentResponse;
  }
}
