/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.agenticai.aiagent.model.SystemPromptProvenance;
import io.camunda.connector.agenticai.aiagent.model.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.LinkedHashMap;

/** AI Agent Sub-process v2 connector (LLM-provider layer). Job worker on an ad-hoc sub-process. */
@OutboundConnector(
    name = AgentSubProcessV2Function.JOB_WORKER_NAME,
    type = AgentSubProcessV2Function.JOB_WORKER_TYPE,
    inputVariables = {
      AgentProcessVariables.AD_HOC_SUB_PROCESS_ELEMENTS,
      AgentProcessVariables.AGENT_CONTEXT,
      AgentProcessVariables.TOOL_CALL_RESULTS,
      AgentProcessVariables.PROVIDER,
      AgentProcessVariables.DATA
    },
    withLease = true)
public class AgentSubProcessV2Function implements AgentConnectorFunction {

  public static final String JOB_WORKER_NAME = "AI Agent Sub-process";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:aiagent:subprocess:2";

  private final AgentSubProcessRequestHandler agentRequestHandler;
  private final LinkedSystemPromptResolver linkedSystemPromptResolver;

  public AgentSubProcessV2Function(
      AgentSubProcessRequestHandler agentRequestHandler,
      LinkedSystemPromptResolver linkedSystemPromptResolver) {
    this.agentRequestHandler = agentRequestHandler;
    this.linkedSystemPromptResolver = linkedSystemPromptResolver;
  }

  @Override
  public AgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(AgentSubProcessV2Request.class);
    var inlinePrompt =
        request.data().systemPrompt() == null ? null : request.data().systemPrompt().prompt();
    var linkedPrompt =
        linkedSystemPromptResolver.resolve(context.getJobContext().getCustomHeaders());
    var data =
        linkedPrompt == null
            ? request.data()
            : request
                .data()
                .withSystemPrompt(
                    new SystemPromptConfiguration(
                        LinkedSystemPromptResolver.compose(inlinePrompt, linkedPrompt)));
    var executionContext =
        new AgentSubProcessExecutionContext(
            context.getJobContext(),
            data,
            request.agentContext(),
            request.toolCallResults(),
            request.toolElements(),
            request.provider());
    var response = agentRequestHandler.handleRequest(executionContext);
    if (linkedPrompt == null
        || !(response.variables().get(AgentProcessVariables.AGENT_RESPONSE)
            instanceof AgentSubProcessResponse agentResponse)) {
      return response;
    }
    // Persist which governed resource version the engine pinned, so Operate can show it.
    var provenance =
        new SystemPromptProvenance(
            "linked",
            linkedPrompt.promptId(),
            linkedPrompt.binding(),
            linkedPrompt.version(),
            data.systemPrompt().prompt());
    var variables = new LinkedHashMap<>(response.variables());
    variables.put(
        AgentProcessVariables.AGENT_RESPONSE, agentResponse.withSystemPrompt(provenance));
    return response.withVariables(variables);
  }
}
