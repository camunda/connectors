/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandler;

/**
 * AI Agent job worker implementation (acting on an ad-hoc sub-process).
 *
 * <p>Type and timeout can be overridden by setting the following environment variables:
 *
 * <ul>
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TYPE
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TIMEOUT
 * </ul>
 */
public class AiAgentJobWorker {

  public static final String JOB_WORKER_NAME = "AI Agent Job Worker";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent-job-worker:1";

  public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
  public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
  public static final String AGENT_RESPONSE_VARIABLE = "agent";
  public static final String TOOL_CALL_RESULT_VARIABLE = "toolCallResult";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String PROVIDER_VARIABLE = "provider";
  public static final String DATA_VARIABLE = "data";
  public static final String TOOL_CALL_VARIABLE = "toolCall";

  private final AiAgentJobWorkerHandler jobWorkerHandler;

  public AiAgentJobWorker(AiAgentJobWorkerHandler jobWorkerHandler) {
    this.jobWorkerHandler = jobWorkerHandler;
  }

  @JobWorker(
      name = JOB_WORKER_NAME,
      type = JOB_WORKER_TYPE,
      fetchVariables = {
        AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
        AGENT_CONTEXT_VARIABLE,
        TOOL_CALL_RESULTS_VARIABLE,
        PROVIDER_VARIABLE,
        DATA_VARIABLE
      },
      autoComplete = false)
  public void execute(final JobClient jobClient, final ActivatedJob job) throws Exception {
    jobWorkerHandler.handle(jobClient, job);
  }
}
