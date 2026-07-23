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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceClientVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
class AgentSubProcessAgentInstanceTests extends BaseAgentSubProcessTest {

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
  void shouldUpdateAgentInstanceStatusAndMetricsForToolCallAndFinalAnswerTurns() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null, 10, 20, ToolCall.of("call-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
        Turn.text("The superflux calculation of 5 and 3 is complete.", 15, 25));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3")));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
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
                Map.of("userPrompt", "Calculate the superflux product of 5 and 3, twice")));

    // modelCalls=3, inputTokens=10+10+15=35, outputTokens=20+20+25=65, toolCalls=2
    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
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

  /**
   * Two tools completing at genuinely different real times (a fast script task and an HTTP download
   * delayed via WireMock) in the same tool-calling round: each TOOL_RESULT's {@code completedAt}
   * must reflect its own completion, not a shared turn-end timestamp. Regression guard for #7597.
   */
  @Test
  void shouldRecordDistinctCompletedAtPerToolBasedOnActualCompletionTime() throws Exception {
    final var slowFileUrl = wireMock.getHttpBaseUrl() + "/slow-test.pdf";
    stubFor(
        get(urlPathEqualTo("/slow-test.pdf"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withBodyFile("test.pdf")
                    .withHeader("Content-Type", "application/pdf")
                    .withFixedDelay(3000)));

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null,
            10,
            20,
            ToolCall.of("fast-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of("slow-001", "Download_A_File", "{\"url\": \"%s\"}".formatted(slowFileUrl))),
        Turn.text("Done.", 15, 25));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of("userPrompt", "Calculate the superflux product and download a file")));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(25, 45), 2)));

    AgentInstanceClientVerifier.verify(agentInstanceClient)
        .createdInstance()
        .toolCallTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 2),
            turn ->
                turn.fromUserPrompt("Calculate the superflux product and download a file")
                    .callingTools("SuperfluxProduct", "Download_A_File"))
        .finalAnswerTurn(
            new AgentMetrics(1, new AgentMetrics.TokenUsage(15, 25), 0),
            turn -> {
              turn.fromToolResults().answering("Done.");
              assertThat(turn.toolResultCompletedAt("fast-001"))
                  .as("fast tool completed before slow tool")
                  .isBefore(turn.toolResultCompletedAt("slow-001").minus(Duration.ofSeconds(2)));
            })
        .noMoreInteractions();
  }
}
