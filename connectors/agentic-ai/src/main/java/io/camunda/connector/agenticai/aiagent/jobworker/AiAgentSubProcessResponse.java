/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record AiAgentSubProcessResponse(
    @Nullable AgentResponse agentResponse,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances,
    Map<String, Object> variables)
    implements AdHocSubProcessConnectorResponse, AiAgentSubProcessResponseBuilder.With {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentSubProcessResponse.class);

  public static AiAgentSubProcessResponseBuilder builder() {
    return AiAgentSubProcessResponseBuilder.builder();
  }

  @Override
  public Object responseValue() {
    return agentResponse;
  }

  @Override
  public boolean supportsIgnoreError() {
    return false;
  }

  @Override
  public Map<String, Object> resolveCompletionVariables(Map<String, Object> resultVariables) {
    return variables;
  }

  @Override
  public List<ElementActivation> elementActivations() {
    if (agentResponse == null) {
      return List.of();
    }

    return agentResponse.toolCalls().stream()
        .map(
            toolCall -> {
              if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Activating tool {}: {}", toolCall.metadata().name(), toolCall);
              } else {
                LOGGER.debug("Activating tool {}", toolCall.metadata().name());
              }

              return (ElementActivation)
                  new ToolCallElementActivation(
                      toolCall.metadata().name(),
                      Map.ofEntries(
                          Map.entry(AiAgentJobWorker.TOOL_CALL_VARIABLE, toolCall),
                          // Creating empty toolCallResult variable to avoid variable
                          // to bubble up in the upper scopes while merging variables on
                          // ad-hoc sub-process inner instance completion.
                          Map.entry(AiAgentJobWorker.TOOL_CALL_RESULT_VARIABLE, "")));
            })
        .toList();
  }

  private record ToolCallElementActivation(String elementId, Map<String, Object> variables)
      implements ElementActivation {}
}
