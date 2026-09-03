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
import io.camunda.connector.runtime.core.secret.SecretFilter;
import java.util.List;
import java.util.function.Consumer;

public interface JobWorkerAgentExecutionContextFactory {

  /** Preserved for callers compiled against it, which have nowhere to keep the resolved values. */
  default JobWorkerAgentExecutionContext createExecutionContext(
      final JobClient jobClient, final ActivatedJob job, final SecretFilter secretFilter) {
    return createExecutionContext(jobClient, job, secretFilter, values -> {});
  }

  /**
   * Binds the job's input, reporting the secret values substituted into it to {@code
   * capturedSecrets} whether the binding succeeds or fails. A caller reporting an error has to
   * redact it with those values: a secret rotated since the input was bound no longer reads back,
   * and the value the message actually carries is this one.
   */
  JobWorkerAgentExecutionContext createExecutionContext(
      final JobClient jobClient,
      final ActivatedJob job,
      final SecretFilter secretFilter,
      final Consumer<List<String>> capturedSecrets);
}
