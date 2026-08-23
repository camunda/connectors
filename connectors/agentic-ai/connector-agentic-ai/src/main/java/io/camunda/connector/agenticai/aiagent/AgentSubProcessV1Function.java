/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentSubProcessV1Request;
import io.camunda.connector.agenticai.aiagent.model.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * AI Agent job worker implementation (acting on an ad-hoc sub-process).
 *
 * <p>Type and timeout can be overridden by setting the following environment variables:
 *
 * <ul>
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TYPE
 *   <li>CONNECTOR_AI_AGENT_JOB_WORKER_TIMEOUT
 * </ul>
 *
 * @deprecated Retained only as a backward-compatibility shim for existing v1 process models; new
 *     process models should use the v2 AI Agent connector.
 */
@Deprecated
@OutboundConnector(
    name = AgentSubProcessV1Function.JOB_WORKER_NAME,
    type = AgentSubProcessV1Function.JOB_WORKER_TYPE,
    inputVariables = {
      AgentProcessVariables.AD_HOC_SUB_PROCESS_ELEMENTS,
      AgentProcessVariables.AGENT_CONTEXT,
      AgentProcessVariables.TOOL_CALL_RESULTS,
      AgentProcessVariables.PROVIDER,
      AgentProcessVariables.DATA
    },
    withLease = true)
public class AgentSubProcessV1Function implements AgentConnectorFunction {

  public static final String JOB_WORKER_NAME = "AI Agent Job Worker";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent-job-worker:1";

  private final AgentSubProcessRequestHandler agentRequestHandler;
  private final V1ToV2ProviderConfigurationMapper providerConfigurationMapper;

  public AgentSubProcessV1Function(
      AgentSubProcessRequestHandler agentRequestHandler,
      V1ToV2ProviderConfigurationMapper providerConfigurationMapper) {
    this.agentRequestHandler = agentRequestHandler;
    this.providerConfigurationMapper = providerConfigurationMapper;
  }

  @Override
  public AgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(AgentSubProcessV1Request.class);
    var executionContext = buildExecutionContext(context.getJobContext(), request);
    return agentRequestHandler.handleRequest(executionContext);
  }

  private AgentSubProcessExecutionContext buildExecutionContext(
      JobContext jobContext, AgentSubProcessV1Request request) {
    var nativeConfig = providerConfigurationMapper.map(request.provider());
    return new AgentSubProcessExecutionContext(
        jobContext,
        request.data(),
        request.agentContext(),
        request.toolCallResults(),
        request.toolElements(),
        nativeConfig);
  }
}
