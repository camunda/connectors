/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ContentFilteredTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceClientVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
class AgentTaskAgentInstanceTests extends BaseAgentTaskTest {

  @MockitoSpyBean private AgentInstanceClient agentInstanceClient;

  /**
   * Happy path: one tool call (SuperfluxProduct) followed by a final answer.
   *
   * <p>Expected agent instance update sequence:
   *
   * <pre>
   * Init:   create agent instance
   * Turn 1: THINKING (sync) → LLM returns tool call → TOOL_CALLING + delta (sync — outbound
   *         connector element instance always completes at job completion → immediate PATCH required)
   * Tool:   SuperfluxProduct executes as BPMN script task
   * Turn 2: THINKING (sync) → LLM returns final answer → IDLE + delta (sync)
   * </pre>
   *
   * <p>The outbound connector always uses the immediate update path: the service task element
   * instance closes on every successful job completion, so all PATCHes are synchronous. Per turn
   * the order is: THINKING → history (before/after chat) → metrics+status.
   */
  @Test
  void shouldUpdateAgentInstanceStatusAndMetricsForToolCallAndFinalAnswerTurns() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null, 10, 20, ToolCall.of("call-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
        Turn.text("The superflux calculation of 5 and 3 is complete.", 15, 25));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                e -> e.property("provider.openai.model.model", "gpt-4o"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3")));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasAgentInstanceKey()
              .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(25, 45), 1));
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey());
        });

    AgentInstanceClientVerifier.verify(agentInstanceClient)
        .createdInstance()
        .toolCallTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1),
            turn ->
                turn.fromUserPrompt("Calculate the superflux product of 5 and 3")
                    .callingTool("SuperfluxProduct"))
        .finalAnswerTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(15, 25), 0),
            turn ->
                turn.fromToolResults()
                    .answering("The superflux calculation of 5 and 3 is complete."))
        .noMoreInteractions();

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasStatus(AgentInstanceStatus.COMPLETED)
        .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(25, 45), 1))
        .hasDefinition("gpt-4o", "openai")
        .hasToolsContaining("SuperfluxProduct")
        .createdWithConfigurationItem("gpt-4o", "openai", 10)
        .hasConfigurationItemsAtLeast(2)
        .hasConversationRoles(
            AgentInstanceHistoryRole.USER,
            AgentInstanceHistoryRole.ASSISTANT,
            AgentInstanceHistoryRole.TOOL_RESULT,
            AgentInstanceHistoryRole.ASSISTANT)
        .verify();
  }

  /**
   * Two consecutive tool calls (SuperfluxProduct × 2) followed by a final answer.
   *
   * <p>Expected agent instance update sequence:
   *
   * <pre>
   * Init:   create agent instance
   * Turn 1: THINKING (sync) → tool call → TOOL_CALLING + delta (sync — outbound always immediate)
   * Tool:   SuperfluxProduct executes as BPMN script task
   * Turn 2: THINKING (sync) → tool call → TOOL_CALLING + delta (sync)
   * Tool:   SuperfluxProduct executes as BPMN script task
   * Turn 3: THINKING (sync) → final answer → IDLE + delta (sync)
   * </pre>
   */
  @Test
  void shouldUpdateAgentInstanceStatusAndMetricsAcrossMultipleToolCallRounds() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null, 10, 20, ToolCall.of("call-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
        Turn.toolCalls(
            null, 10, 20, ToolCall.of("call-002", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
        Turn.text("The superflux calculation of 5 and 3 is complete.", 15, 25));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                e -> e.property("provider.openai.model.model", "gpt-4o"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3, twice")));

    // modelCalls=3, inputTokens=10+10+15=35, outputTokens=20+20+25=65, toolCalls=2
    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasAgentInstanceKey()
              .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(35, 65), 2));
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey());
        });

    // turn 1 input is the user prompt; turns 2 and 3 inputs are tool results; assistant items:
    // turns 1 and 2 carry a tool call, turn 3 is the final answer.
    AgentInstanceClientVerifier.verify(agentInstanceClient)
        .createdInstance()
        .toolCallTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1),
            turn ->
                turn.fromUserPrompt("Calculate the superflux product of 5 and 3, twice")
                    .callingTool("SuperfluxProduct"))
        .toolCallTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1),
            turn -> turn.fromToolResults().callingTool("SuperfluxProduct"))
        .finalAnswerTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(15, 25), 0),
            turn ->
                turn.fromToolResults()
                    .answering("The superflux calculation of 5 and 3 is complete."))
        .noMoreInteractions();

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasStatus(AgentInstanceStatus.COMPLETED)
        .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(35, 65), 2))
        .hasDefinition("gpt-4o", "openai")
        .hasToolsContaining("SuperfluxProduct")
        .createdWithConfigurationItem("gpt-4o", "openai", 10)
        .hasConfigurationItemsAtLeast(2)
        .hasConversationRoles(
            AgentInstanceHistoryRole.USER,
            AgentInstanceHistoryRole.ASSISTANT,
            AgentInstanceHistoryRole.TOOL_RESULT,
            AgentInstanceHistoryRole.ASSISTANT,
            AgentInstanceHistoryRole.TOOL_RESULT,
            AgentInstanceHistoryRole.ASSISTANT)
        .verify();
  }

  /**
   * A job failure (max model calls reached, raising an incident) while the instance is stuck at
   * {@code TOOL_CALLING} must still report {@code IDLE} so Operate doesn't keep showing a stale
   * in-progress status for an agent that has stopped (camunda/connectors#8591). Deliberately trips
   * the limit right after a tool-calling turn rather than a plain-text turn: a plain-text turn
   * already leaves the instance {@code IDLE} via the normal turn-completion path, which would make
   * this test pass even without the fix.
   */
  @Test
  void reportsIdleStatusWhenMaximumModelCallsIsReachedWhileToolCalling() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null, 10, 20, ToolCall.of("call-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")));

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                elementTemplate -> elementTemplate.property("data.limits.maxModelCalls", "1"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3")));

    assertIncident(
        zeebeTest,
        incident ->
            assertThat(incident.getErrorMessage())
                .startsWith("Maximum number of model calls reached (modelCalls: 1, limit: 1)"));

    // resolve the agent instance from the process instance -- the job never completed, so no
    // process variable carries the key
    final var processInstanceKey = zeebeTest.getProcessInstanceEvent().getProcessInstanceKey();
    final var agentInstanceKey =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .execute()
            .singleItem()
            .getAgentInstanceKey();

    verify(agentInstanceClient)
        .reportIdleOnFailure(
            any(AgentExecutionContext.class), eq(AgentInstanceKey.of(agentInstanceKey)));

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasStatus(AgentInstanceStatus.IDLE)
        .verify();
  }

  /**
   * A brand-new agent instance whose very first model call is rejected (content-filtered), raising
   * an incident before any turn ever completes, must still report {@code IDLE}. Regression guard
   * for the gap where the recoverable key was only ever read from {@code
   * executionContext.initialAgentContext()} (the pre-invocation state, always {@code null} for a
   * first job) instead of the in-flight {@code AgentContext} that {@code initializeAgent()} just
   * created (camunda/connectors#8591).
   */
  @Test
  void reportsIdleStatusWhenFirstModelCallOnANewAgentInstanceIsContentFiltered() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        new ContentFilteredTurnStub("I can help, but", 10, 20));

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                elementTemplate -> elementTemplate.property("retryCount", "1"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3")));

    final var processInstanceKey = zeebeTest.getProcessInstanceEvent().getProcessInstanceKey();
    final var agentInstanceKey =
        camundaClient
            .newAgentInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .execute()
            .singleItem()
            .getAgentInstanceKey();

    verify(agentInstanceClient)
        .reportIdleOnFailure(
            any(AgentExecutionContext.class), eq(AgentInstanceKey.of(agentInstanceKey)));

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey)
        .hasStatus(AgentInstanceStatus.IDLE)
        .verify();
  }
}
