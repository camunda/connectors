/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.StandardConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionListener;
import org.springframework.lang.Nullable;

/**
 * Response type for the AI Agent task flavor (outbound connector on a service task). Delegates job
 * completion callbacks to the provided listener.
 */
public record AiAgentTaskConnectorResponse(
    @Nullable AgentResponse agentResponse, @Nullable JobCompletionListener completionListener)
    implements StandardConnectorResponse, JobCompletionListener {

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
