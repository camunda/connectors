/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.runtime.core.outbound.ConnectorResult.ErrorResult;

sealed interface JobWorkerAgentResult
    permits JobWorkerAgentResult.AgentSuccessResult, JobWorkerAgentResult.AgentErrorResult {
  Object responseValue();

  record AgentSuccessResult(JobWorkerAgentCompletion completion) implements JobWorkerAgentResult {
    @Override
    public Object responseValue() {
      return completion.agentResponse();
    }
  }

  record AgentErrorResult(ErrorResult errorResult) implements JobWorkerAgentResult {
    @Override
    public Object responseValue() {
      return errorResult.responseValue();
    }
  }
}
