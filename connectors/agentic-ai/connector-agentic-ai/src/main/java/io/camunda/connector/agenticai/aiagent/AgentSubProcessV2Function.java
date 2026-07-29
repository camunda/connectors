/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentSubProcessV2Request;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/** AI Agent Sub-process v2 connector (LLM-provider layer). Job worker on an ad-hoc sub-process. */
@OutboundConnector(
    name = AgentSubProcessV2Function.JOB_WORKER_NAME,
    type = AgentSubProcessV2Function.JOB_WORKER_TYPE,
    inputVariables = {
      AgentSubProcessV2Function.AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
      AgentSubProcessV2Function.AGENT_CONTEXT_VARIABLE,
      AgentSubProcessV2Function.TOOL_CALL_RESULTS_VARIABLE,
      AgentSubProcessV2Function.PROVIDER_VARIABLE,
      AgentSubProcessV2Function.DATA_VARIABLE
    })
public class AgentSubProcessV2Function implements AgentConnectorFunction {

  public static final String JOB_WORKER_NAME = "AI Agent Sub-process";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent:subprocess:2";

  public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
  public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String PROVIDER_VARIABLE = "provider";
  public static final String DATA_VARIABLE = "data";

  private final AgentSubProcessRequestHandler agentRequestHandler;

  public AgentSubProcessV2Function(AgentSubProcessRequestHandler agentRequestHandler) {
    this.agentRequestHandler = agentRequestHandler;
  }

  @Override
  public AgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(AgentSubProcessV2Request.class);
    var executionContext =
        new AgentSubProcessExecutionContext(
            context.getJobContext(),
            request.data(),
            request.agentContext(),
            request.toolCallResults(),
            request.toolElements(),
            request.provider());
    return agentRequestHandler.handleRequest(executionContext);
  }
}
