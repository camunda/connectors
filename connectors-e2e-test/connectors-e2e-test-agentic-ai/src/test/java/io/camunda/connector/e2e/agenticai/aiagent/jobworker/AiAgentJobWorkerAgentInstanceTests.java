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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceClientVerifier;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
class AiAgentJobWorkerAgentInstanceTests extends BaseAiAgentJobWorkerTest {

  @MockitoSpyBean private AgentInstanceClient agentInstanceClient;

  /**
   * Happy path: one tool call (SuperfluxProduct) followed by a final answer.
   *
   * <p>Expected agent instance update sequence:
   *
   * <pre>
   * Init:   create agent instance
   * Turn 1: THINKING (sync) → LLM returns tool call → TOOL_CALLING + delta (deferred via onJobCompleted)
   * Tool:   SuperfluxProduct executes as script task
   * Turn 2: THINKING (sync) → LLM returns final answer → IDLE + delta (sync, element instance about to close)
   * </pre>
   *
   * <p>Turn 1: THINKING is synchronous; history items (before/after chat) are synchronous;
   * TOOL_CALLING is deferred (AHSP stays open after completionConditionFulfilled=false → element
   * instance survives job completion) and fires before turn 2 begins. Turn 2: THINKING + history +
   * IDLE are synchronous (AHSP closes after completionConditionFulfilled=true → element instance
   * completes at job completion).
   */
  @Test
  void shouldUpdateAgentInstanceStatusAndMetricsForToolCallAndFinalAnswerTurns() throws Exception {
    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("agent-turns")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody("call-001")))
            .willSetStateTo("turn-2"));

    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("agent-turns")
            .whenScenarioStateIs("turn-2")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(finalAnswerResponseBody())));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3")));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse)
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

    // Verify on the engine that the agent instance the connector created actually landed on the
    // broker and is queryable from secondary storage.
    assertAgentInstanceCreatedOnEngine(agentInstanceKey.get(), "test-model");
  }

  /**
   * Two consecutive tool calls (SuperfluxProduct × 2) followed by a final answer.
   *
   * <p>Expected agent instance update sequence:
   *
   * <pre>
   * Init:   create agent instance
   * Turn 1: THINKING (sync) → tool call → TOOL_CALLING + delta (deferred via onJobCompleted)
   * Tool:   SuperfluxProduct executes
   * Turn 2: THINKING (sync) → tool call → TOOL_CALLING + delta (deferred via onJobCompleted)
   * Tool:   SuperfluxProduct executes
   * Turn 3: THINKING (sync) → final answer → IDLE + delta (sync, element instance about to close)
   * </pre>
   */
  @Test
  void shouldUpdateAgentInstanceStatusAndMetricsAcrossMultipleToolCallRounds() throws Exception {
    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("multi-tool-turns")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody("call-001")))
            .willSetStateTo("turn-2"));

    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("multi-tool-turns")
            .whenScenarioStateIs("turn-2")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody("call-002")))
            .willSetStateTo("turn-3"));

    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("multi-tool-turns")
            .whenScenarioStateIs("turn-3")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(finalAnswerResponseBody())));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3, twice")));

    // modelCalls=3, inputTokens=10+10+15=35, outputTokens=20+20+25=65, toolCalls=2
    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse)
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

    assertAgentInstanceCreatedOnEngine(agentInstanceKey.get(), "test-model");
  }

  // TODO provision: assert the conversation history once the read API is available.
  // The spec defines POST /v2/agent-instances/{key}/history/search (operationId
  // searchAgentInstanceHistory), but it is not yet implemented in the gateway controller, exposed
  // on the Java client, nor backed by an RDBMS handler for AGENT_HISTORY. Once available, assert
  // the
  // ordered USER / ASSISTANT / TOOL_RESULT items (content, iteration, assistant tool calls +
  // metrics) here, mirroring assertAgentInstanceCreatedOnEngine (Awaitility + eventual
  // consistency).
  //
  // TODO follow-up: assert the accumulated metrics + terminal status via
  // newAgentInstanceGetRequest.
  // The exporter handles UPDATED, but the read-back metrics did not converge to the final totals
  // within a 60s poll in this embedded test — investigate the visibility lag (exporter flush /
  // consistency, delta-vs-accumulated in the UPDATED record), then pin: metrics == accumulated
  // {modelCalls, toolCalls, inputTokens, outputTokens}; status IDLE/COMPLETED.

  // turn 1: tool call to SuperfluxProduct (inputTokens=10, outputTokens=20)
  private static String toolCallResponseBody(String toolCallId) {
    return """
        {
          "id": "chatcmpl-turn2",
          "object": "chat.completion",
          "model": "gpt-4o",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": null,
              "tool_calls": [{
                "id": "%s",
                "type": "function",
                "function": {
                  "name": "SuperfluxProduct",
                  "arguments": "{\\"a\\": 5, \\"b\\": 3}"
                }
              }]
            },
            "finish_reason": "tool_calls"
          }],
          "usage": {
            "prompt_tokens": 10,
            "completion_tokens": 20,
            "total_tokens": 30
          }
        }
        """
        .formatted(toolCallId);
  }

  // turn 2: final answer, no tool calls (inputTokens=15, outputTokens=25)
  private static String finalAnswerResponseBody() {
    return """
        {
          "id": "chatcmpl-turn2",
          "object": "chat.completion",
          "model": "gpt-4o",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "The superflux calculation of 5 and 3 is complete."
            },
            "finish_reason": "stop"
          }],
          "usage": {
            "prompt_tokens": 15,
            "completion_tokens": 25,
            "total_tokens": 40
          }
        }
        """;
  }
}
