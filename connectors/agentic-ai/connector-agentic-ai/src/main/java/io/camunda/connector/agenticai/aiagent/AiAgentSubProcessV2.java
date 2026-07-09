/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequestV2;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/** AI Agent Sub-process v2 connector (LLM-provider layer). Job worker on an ad-hoc sub-process. */
@OutboundConnector(
    name = AiAgentSubProcessV2.JOB_WORKER_NAME,
    type = AiAgentSubProcessV2.JOB_WORKER_TYPE,
    inputVariables = {
      AiAgentSubProcessV2.AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
      AiAgentSubProcessV2.AGENT_CONTEXT_VARIABLE,
      AiAgentSubProcessV2.TOOL_CALL_RESULTS_VARIABLE,
      AiAgentSubProcessV2.CONFIGURATION_VARIABLE,
      AiAgentSubProcessV2.DATA_VARIABLE
    })
public class AiAgentSubProcessV2 implements AgentConnectorFunction {

  public static final String JOB_WORKER_NAME = "AI Agent Sub-process";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent:subprocess:2";

  public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
  public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String CONFIGURATION_VARIABLE = "configuration";
  public static final String DATA_VARIABLE = "data";

  private final JobWorkerAgentRequestHandler agentRequestHandler;

  public AiAgentSubProcessV2(JobWorkerAgentRequestHandler agentRequestHandler) {
    this.agentRequestHandler = agentRequestHandler;
  }

  @Override
  public AiAgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(JobWorkerAgentRequestV2.class);
    var config = request.configuration();
    var executionContext =
        new JobWorkerAgentExecutionContext(
            context.getJobContext(),
            request.data(),
            request.agentContext(),
            request.toolCallResults(),
            request.toolElements(),
            new LlmProviderChatModelApiConfiguration(config),
            config.modelId(),
            config.type());
    return agentRequestHandler.handleRequest(executionContext);
  }
}
