/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record AiAgentSubProcessResponse(
    @Nullable AgentResponse responseValue,
    Map<String, Object> variables,
    List<ElementActivation> elementActivations,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances)
    implements AdHocSubProcessConnectorResponse, AiAgentSubProcessResponseBuilder.With {

  public static AiAgentSubProcessResponseBuilder builder() {
    return AiAgentSubProcessResponseBuilder.builder();
  }

  public record ToolCallElementActivation(String elementId, Map<String, Object> variables)
      implements ElementActivation {}
}
