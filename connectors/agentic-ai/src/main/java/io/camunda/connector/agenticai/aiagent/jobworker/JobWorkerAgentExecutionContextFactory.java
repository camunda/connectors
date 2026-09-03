/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import java.util.List;

public interface JobWorkerAgentExecutionContextFactory {
  /**
   * Collects into {@code capturedSecretValues} every value substituted into the job's input,
   * whether the binding then succeeds or fails, so that an error reported for this job can be
   * redacted with the values the agent was actually handed rather than with whatever the secret
   * store holds by the time the error is built.
   */
  JobWorkerAgentExecutionContext createExecutionContext(
      final JobClient jobClient, final ActivatedJob job, final List<String> capturedSecretValues);
}
