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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Future REST assertion imports (uncomment together with assertAgentInstanceState):
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.awaitility.Awaitility.await;
// import io.camunda.client.api.search.enums.AgentInstanceStatus;
// import io.camunda.process.test.api.CamundaProcessTestContext;
// import java.time.Duration;

@SlowTest
class L4JAiAgentJobWorkerAgentInstanceTests extends BaseAiAgentJobWorkerTest {

  // Kept for future REST-based assertions (see assertAgentInstanceState below)
  // @Autowired private CamundaProcessTestContext processTestContext;

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
   */
  @Test
  void shouldUpdateAgentInstanceStatusAndMetricsForToolCallAndFinalAnswerTurns(
      WireMockRuntimeInfo wireMock) throws Exception {
    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("agent-turns")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody()))
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

    userFeedbackVariables.set(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                e ->
                    e.property("provider.type", "openaiCompatible")
                        .property(
                            "provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
                        .property("provider.openaiCompatible.model.model", "gpt-4o"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3"))
            .waitForProcessCompletion();

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasAgentInstanceKey()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(25, 45), 1)));

    // Turn 1: THINKING is synchronous; TOOL_CALLING is deferred (AHSP stays open after
    // completionConditionFulfilled=false → element instance survives job completion).
    // Turn 2: both THINKING and IDLE are synchronous (AHSP closes after
    // completionConditionFulfilled=true
    // → element instance dies at job completion → immediate PATCH required).
    var inOrder = inOrder(agentInstanceClient);
    inOrder.verify(agentInstanceClient).create(any());
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.TOOL_CALLING)
                    .delta(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1))
                    .build()));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.IDLE)
                    .delta(new AgentMetrics(1, new AgentMetrics.TokenUsage(15, 25), 0))
                    .build()));
    verifyNoMoreInteractions(agentInstanceClient);

    // Future: verify accumulated state via the agent instance REST search endpoint.
    // The search endpoint is updated by the RDBMS exporter; the embedded test engine only
    // populates primary storage (get by key) which is not updated by exporters.
    //
    // assertAgentInstanceState(agentInstanceKey, "IDLE", 2, 1, 25L, 45L);
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
  void shouldUpdateAgentInstanceStatusAndMetricsAcrossMultipleToolCallRounds(
      WireMockRuntimeInfo wireMock) throws Exception {
    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("multi-tool-turns")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody()))
            .willSetStateTo("turn-2"));

    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .inScenario("multi-tool-turns")
            .whenScenarioStateIs("turn-2")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toolCallResponseBody()))
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

    userFeedbackVariables.set(userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                e ->
                    e.property("provider.type", "openaiCompatible")
                        .property(
                            "provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
                        .property("provider.openaiCompatible.model.model", "gpt-4o"),
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3, twice"))
            .waitForProcessCompletion();

    // modelCalls=3, inputTokens=10+10+15=35, outputTokens=20+20+25=65, toolCalls=2
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasAgentInstanceKey()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(35, 65), 2)));

    var inOrder = inOrder(agentInstanceClient);
    inOrder.verify(agentInstanceClient).create(any());
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.TOOL_CALLING)
                    .delta(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1))
                    .build()));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.TOOL_CALLING)
                    .delta(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 1))
                    .build()));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.IDLE)
                    .delta(new AgentMetrics(1, new AgentMetrics.TokenUsage(15, 25), 0))
                    .build()));
    verifyNoMoreInteractions(agentInstanceClient);
  }

  // Kept for future — asserts agent instance state via the REST search endpoint.
  //
  // private void assertAgentInstanceState(
  //     long agentInstanceKey,
  //     String expectedStatus,
  //     int expectedModelCalls,
  //     int expectedToolCalls,
  //     long expectedInputTokens,
  //     long expectedOutputTokens) {
  //
  //   await()
  //       .alias("agent instance state via REST search")
  //       .atMost(Duration.ofSeconds(15))
  //       .untilAsserted(
  //           () ->
  //               assertThat(
  //                       camundaClient
  //                           .newAgentInstanceSearchRequest()
  //                           .filter(f -> f.agentInstanceKey(agentInstanceKey))
  //                           .execute()
  //                           .items())
  //                   .hasSize(1)
  //                   .first()
  //                   .satisfies(
  //                       agentInstance -> {
  //                         assertThat(agentInstance.getStatus())
  //                             .extracting(AgentInstanceStatus::name)
  //                             .isEqualTo(expectedStatus);
  //                         assertThat(agentInstance.getMetrics())
  //                             .isNotNull()
  //                             .satisfies(
  //                                 metrics -> {
  //                                   assertThat(metrics.getModelCalls())
  //                                       .isEqualTo(expectedModelCalls);
  //                                   assertThat(metrics.getToolCalls())
  //                                       .isEqualTo(expectedToolCalls);
  //                                   assertThat(metrics.getInputTokens())
  //                                       .isEqualTo(expectedInputTokens);
  //                                   assertThat(metrics.getOutputTokens())
  //                                       .isEqualTo(expectedOutputTokens);
  //                                 });
  //                       }));
  // }

  // turn 1: tool call to SuperfluxProduct (inputTokens=10, outputTokens=20)
  private static String toolCallResponseBody() {
    return """
        {
          "id": "chatcmpl-turn1",
          "object": "chat.completion",
          "model": "gpt-4o",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": null,
              "tool_calls": [{
                "id": "call-001",
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
        """;
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
