/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionListener;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record AiAgentSubProcessConnectorResponse(
    @Nullable AgentResponse responseValue,
    Map<String, Object> variables,
    List<ElementActivation> elementActivations,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances,
    @Nullable JobCompletionListener completionListener)
    implements AdHocSubProcessConnectorResponse,
        JobCompletionListener,
        AiAgentSubProcessConnectorResponseBuilder.With {

  public static AiAgentSubProcessConnectorResponseBuilder builder() {
    return AiAgentSubProcessConnectorResponseBuilder.builder();
  }

  @Override
  public void onJobCompleted() {
    if (completionListener != null) {
      completionListener.onJobCompleted();
    }
  }

  @Override
  public void onJobCompletionFailed(JobCompletionFailure failure) {
    if (completionListener != null) {
      completionListener.onJobCompletionFailed(failure);
    }
  }

  public record ToolCallElementActivation(String elementId, Map<String, Object> variables)
      implements ElementActivation {}
}
