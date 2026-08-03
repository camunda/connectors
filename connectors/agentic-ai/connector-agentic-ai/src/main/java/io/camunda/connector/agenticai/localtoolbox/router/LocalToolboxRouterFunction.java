/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.router;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.localtoolbox.LocalToolboxErrorCodes;
import io.camunda.connector.agenticai.localtoolbox.router.LocalToolboxRouterConnectorResponse.ToolCallElementActivation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Drives the toolbox process's own ad-hoc sub-process: a small, non-LLM two-phase state machine
 * mirroring the core agent loop ({@code ai-agent.md} §8-9), simplified to a single, pre-selected
 * tool call decided up front by {@code LocalToolboxClientFunction} instead of chosen per turn by an
 * LLM.
 *
 * <p>Applied to the toolbox's ad-hoc sub-process activity in place of the AI Agent Sub-process
 * template - the referenced process must NOT have the AI Agent template applied.
 *
 * <p>No element template is generated for this connector in v1 (mirroring {@code AiAgentJobWorker},
 * whose ad-hoc sub-process template is produced by a separate transform rather than its own
 * annotations); wire {@code zeebe:taskDefinition type="io.camunda.agenticai:localtoolboxrouter:1"}
 * onto the toolbox's ad-hoc sub-process manually until a dedicated template is added.
 */
@OutboundConnector(
    name = "Local Toolbox Router",
    type = "io.camunda.agenticai:localtoolboxrouter:1",
    inputVariables = {
      LocalToolboxRouterFunction.TOOL_CALL_VARIABLE,
      LocalToolboxRouterFunction.TOOL_CALL_RESULTS_VARIABLE
    })
public class LocalToolboxRouterFunction implements OutboundConnectorFunction {

  public static final String TOOL_CALL_VARIABLE = "toolCall";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String TOOL_CALL_RESULT_VARIABLE = "toolCallResult";

  private static final String CALL_ID = "call-1";

  @Override
  public LocalToolboxRouterConnectorResponse execute(OutboundConnectorContext context) {
    final var request = context.bindVariables(LocalToolboxRouterRequest.class);
    final var toolCallResults =
        request.toolCallResults() == null ? List.<ToolCallResult>of() : request.toolCallResults();

    final var existingResult =
        toolCallResults.stream().filter(result -> CALL_ID.equals(result.id())).findFirst();
    if (existingResult.isPresent()) {
      return LocalToolboxRouterConnectorResponse.builder()
          .responseValue(null)
          .variables(Map.of(TOOL_CALL_RESULT_VARIABLE, existingResult.get().content()))
          .elementActivations(List.of())
          .completionConditionFulfilled(true)
          .cancelRemainingInstances(false)
          .build();
    }

    final var toolCall = request.toolCall();
    if (toolCall == null) {
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_TOOL_DEFINITIONS,
          "Local toolbox router activated without a '%s' variable".formatted(TOOL_CALL_VARIABLE));
    }

    final var toolCallProcessVariable =
        new ToolCallProcessVariable(CALL_ID, toolCall.name(), toolCall.arguments());
    return LocalToolboxRouterConnectorResponse.builder()
        .responseValue(null)
        .variables(Map.of())
        .elementActivations(
            List.of(
                new ToolCallElementActivation(
                    toolCall.name(),
                    Map.of(
                        TOOL_CALL_VARIABLE,
                        toolCallProcessVariable,
                        TOOL_CALL_RESULT_VARIABLE,
                        ""))))
        .completionConditionFulfilled(false)
        .cancelRemainingInstances(false)
        .build();
  }

  public record LocalToolboxRouterRequest(
      @Nullable ToolCallRequest toolCall, @Nullable List<ToolCallResult> toolCallResults) {

    public record ToolCallRequest(String name, Map<String, Object> arguments) {}
  }
}
