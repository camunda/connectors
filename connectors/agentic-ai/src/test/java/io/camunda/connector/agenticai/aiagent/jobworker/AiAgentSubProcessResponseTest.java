/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS_PROCESS_VARIABLES;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse.ElementActivation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiAgentSubProcessResponseTest {

  @Test
  void completesWithoutToolCalls() {
    var response =
        AiAgentSubProcessResponse.builder()
            .agentResponse(null)
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(false)
            .variables(Map.of())
            .build();

    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();
    assertThat(response.elementActivations()).isEmpty();
    assertThat(response.resolveCompletionVariables(Map.of("ignored", true))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithCompletionConditionFulfilled(boolean cancelRemainingInstances) {
    var variables = Map.<String, Object>of("agent", Map.of("responseText", "Done"));

    var response =
        AiAgentSubProcessResponse.builder()
            .agentResponse(null)
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(variables)
            .build();

    assertThat(response.completionConditionFulfilled()).isTrue();
    assertThat(response.cancelRemainingInstances()).isEqualTo(cancelRemainingInstances);
    assertThat(response.elementActivations()).isEmpty();
    assertThat(response.resolveCompletionVariables(Map.of())).isEqualTo(variables);
  }

  @Test
  void completesWithoutElementActivationWhenAgentResponseHasEmptyToolCalls() {
    var agentResponse =
        AgentResponse.builder().context(AgentContext.empty()).toolCalls(List.of()).build();
    var variables = Map.<String, Object>of("agent", Map.of("responseText", "Done"));

    var response =
        AiAgentSubProcessResponse.builder()
            .agentResponse(agentResponse)
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .variables(variables)
            .build();

    assertThat(response.completionConditionFulfilled()).isTrue();
    assertThat(response.elementActivations()).isEmpty();
    assertThat(response.resolveCompletionVariables(Map.of())).isEqualTo(variables);
  }

  @Test
  void usesRecordVariablesNotResultExpressionVariables() {
    var recordVariables = Map.<String, Object>of("fromRecord", true);
    var resultExpressionVariables = Map.<String, Object>of("fromExpression", true);

    var response =
        AiAgentSubProcessResponse.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .variables(recordVariables)
            .build();

    assertThat(response.resolveCompletionVariables(resultExpressionVariables))
        .isEqualTo(recordVariables);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithActivatingElements(boolean cancelRemainingInstances) {
    var toolCalls = TOOL_CALLS_PROCESS_VARIABLES;
    var agentResponse =
        AgentResponse.builder().context(AgentContext.empty()).toolCalls(toolCalls).build();

    var response =
        AiAgentSubProcessResponse.builder()
            .agentResponse(agentResponse)
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(Map.of("agentContext", AgentContext.empty(), "toolCallResults", List.of()))
            .build();

    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isEqualTo(cancelRemainingInstances);
    assertThat(response.elementActivations())
        .extracting(ElementActivation::elementId)
        .containsExactly("getWeather", "getDateTime");
    assertThat(response.elementActivations())
        .extracting(ElementActivation::variables)
        .allSatisfy(
            vars -> {
              assertThat(vars).containsKey(AiAgentJobWorker.TOOL_CALL_VARIABLE);
              assertThat(vars).containsEntry(AiAgentJobWorker.TOOL_CALL_RESULT_VARIABLE, "");
            });
  }

  @Test
  void doesNotSupportIgnoreError() {
    assertThat(AiAgentSubProcessResponse.builder().build().supportsIgnoreError()).isFalse();
  }

  @Test
  void responseValueIsAgentResponse() {
    var agentResponse =
        AgentResponse.builder().context(AgentContext.empty()).toolCalls(List.of()).build();

    var response = AiAgentSubProcessResponse.builder().agentResponse(agentResponse).build();

    assertThat(response.responseValue()).isSameAs(agentResponse);
  }
}
