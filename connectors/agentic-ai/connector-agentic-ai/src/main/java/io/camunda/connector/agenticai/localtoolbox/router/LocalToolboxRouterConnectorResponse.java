/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.router;

import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Response driving the toolbox's own ad-hoc sub-process: either activates the single requested tool
 * element, or - once its result is available in {@code toolCallResults} - completes the sub-process
 * with that result copied to the process-level {@code toolCallResult} variable.
 *
 * <p>The non-agentic counterpart of {@code AiAgentSubProcessConnectorResponse}: same {@link
 * AdHocSubProcessConnectorResponse} contract, without the LLM-specific response/completion-listener
 * fields.
 */
@AgenticAiRecord
public record LocalToolboxRouterConnectorResponse(
    @Nullable Object responseValue,
    Map<String, Object> variables,
    List<ElementActivation> elementActivations,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances)
    implements AdHocSubProcessConnectorResponse, LocalToolboxRouterConnectorResponseBuilder.With {

  public static LocalToolboxRouterConnectorResponseBuilder builder() {
    return LocalToolboxRouterConnectorResponseBuilder.builder();
  }

  public record ToolCallElementActivation(String elementId, Map<String, Object> variables)
      implements ElementActivation {}
}
