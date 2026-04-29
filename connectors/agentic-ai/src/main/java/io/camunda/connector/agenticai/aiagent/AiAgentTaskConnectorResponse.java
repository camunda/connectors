/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AgentJobCompletionListener;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.StandardConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import org.springframework.lang.Nullable;

/**
 * Response type for the AI Agent task flavor (outbound connector on a service task). Carries an
 * optional {@link AgentJobCompletionListener} so the connector function can dispatch lifecycle
 * callbacks once the Zeebe completion command has resolved.
 */
public record AiAgentTaskConnectorResponse(
    @Nullable AgentResponse agentResponse, @Nullable AgentJobCompletionListener completionListener)
    implements StandardConnectorResponse, AgentJobCompletionListener {

  @Override
  public Object responseValue() {
    return agentResponse;
  }

  @Override
  public void onJobCompleted() {
    if (completionListener != null) {
      completionListener.onJobCompleted();
    }
  }

  @Override
  public void onJobCompletionFailed(JobCompletionFailure failure) {
    if (completionListener != null) {
      completionListener.onJobCompletionFailed(failure);
    }
  }
}
