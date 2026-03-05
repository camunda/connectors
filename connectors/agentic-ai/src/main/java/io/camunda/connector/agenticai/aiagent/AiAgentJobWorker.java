/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobCompletion;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;

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
@OutboundConnector(
    name = AiAgentJobWorker.JOB_WORKER_NAME,
    type = AiAgentJobWorker.JOB_WORKER_TYPE,
    inputVariables = {
      AiAgentJobWorker.AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
      AiAgentJobWorker.AGENT_CONTEXT_VARIABLE,
      AiAgentJobWorker.TOOL_CALL_RESULTS_VARIABLE,
      AiAgentJobWorker.PROVIDER_VARIABLE,
      AiAgentJobWorker.DATA_VARIABLE
    })
public class AiAgentJobWorker implements OutboundConnectorFunction {

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

  private final JobWorkerAgentRequestHandler agentRequestHandler;

  public AiAgentJobWorker(JobWorkerAgentRequestHandler agentRequestHandler) {
    this.agentRequestHandler = agentRequestHandler;
  }

  @Override
  public AiAgentJobCompletion execute(OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(JobWorkerAgentRequest.class);
    var jobContext = new OutboundConnectorAgentJobContext(context);
    var executionContext = new JobWorkerAgentExecutionContext(jobContext, request);
    return agentRequestHandler.handleRequest(executionContext);
  }
}
