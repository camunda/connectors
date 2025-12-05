/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

public interface AiAgentJobWorkerHandler {
  void handle(JobClient jobClient, ActivatedJob job) throws Exception;
}
