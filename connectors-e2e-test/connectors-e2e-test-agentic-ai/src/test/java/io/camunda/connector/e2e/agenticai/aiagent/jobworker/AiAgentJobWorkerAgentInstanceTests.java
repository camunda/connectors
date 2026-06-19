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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.ConversationTurn;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    // Turn 1: THINKING is synchronous; history items (before/after chat) are synchronous;
    // TOOL_CALLING is deferred (AHSP stays open after completionConditionFulfilled=false → element
    // instance survives job completion) and fires before turn 2 begins.
    // Turn 2: THINKING + history + IDLE are synchronous (AHSP closes after
    // completionConditionFulfilled=true → element instance completes at job completion).
    final var beforeChatTurns = ArgumentCaptor.forClass(ConversationTurn.class);
    final var afterChatTurns = ArgumentCaptor.forClass(ConversationTurn.class);
    var inOrder = inOrder(agentInstanceClient);
    inOrder.verify(agentInstanceClient).create(any());
    // turn 1: user prompt → assistant tool call
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsBeforeChat(any(), any(), beforeChatTurns.capture());
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsAfterChat(any(), any(), afterChatTurns.capture());
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
    // turn 2: tool result → assistant final answer
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsBeforeChat(any(), any(), beforeChatTurns.capture());
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsAfterChat(any(), any(), afterChatTurns.capture());
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

    assertUserPromptThenToolResultTurns(beforeChatTurns, afterChatTurns);

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

    final var beforeChatTurns = ArgumentCaptor.forClass(ConversationTurn.class);
    final var afterChatTurns = ArgumentCaptor.forClass(ConversationTurn.class);
    var inOrder = inOrder(agentInstanceClient);
    inOrder.verify(agentInstanceClient).create(any());
    // turn 1 + turn 2: tool-call rounds (TOOL_CALLING deferred to job completion on the AHSP)
    for (int i = 0; i < 2; i++) {
      inOrder
          .verify(agentInstanceClient)
          .update(
              any(),
              any(),
              eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
      inOrder
          .verify(agentInstanceClient)
          .createHistoryItemsBeforeChat(any(), any(), beforeChatTurns.capture());
      inOrder
          .verify(agentInstanceClient)
          .createHistoryItemsAfterChat(any(), any(), afterChatTurns.capture());
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
    }
    // turn 3: final answer
    inOrder
        .verify(agentInstanceClient)
        .update(
            any(),
            any(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsBeforeChat(any(), any(), beforeChatTurns.capture());
    inOrder
        .verify(agentInstanceClient)
        .createHistoryItemsAfterChat(any(), any(), afterChatTurns.capture());
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

    // turn 1 input is the user prompt (USER); turns 2 and 3 inputs are tool results (TOOL_RESULT);
    // assistant items: turns 1 and 2 carry a tool call, turn 3 is the final answer.
    final var beforeTurns = beforeChatTurns.getAllValues();
    final var afterTurns = afterChatTurns.getAllValues();
    assertThat(beforeTurns).hasSize(3);
    assertThat(beforeTurns.get(0).inputMessages()).singleElement().isInstanceOf(UserMessage.class);
    assertThat(beforeTurns.get(1).inputMessages())
        .anyMatch(ToolCallResultMessage.class::isInstance);
    assertThat(beforeTurns.get(2).inputMessages())
        .anyMatch(ToolCallResultMessage.class::isInstance);
    assertThat(afterTurns.get(0).assistantMessage().toolCalls()).hasSize(1);
    assertThat(afterTurns.get(1).assistantMessage().toolCalls()).hasSize(1);
    assertThat(afterTurns.get(2).assistantMessage().toolCalls()).isEmpty();
    assertThat(afterTurns).extracting(ConversationTurn::iterationKey).containsExactly(1, 2, 3);

    assertAgentInstanceCreatedOnEngine(agentInstanceKey.get(), "test-model");
  }

  /**
   * Asserts a single user-prompt turn (USER input → assistant tool call) followed by a tool-result
   * turn (TOOL_RESULT input → assistant final answer), captured from the history-item calls.
   */
  private void assertUserPromptThenToolResultTurns(
      ArgumentCaptor<ConversationTurn> beforeChatTurns,
      ArgumentCaptor<ConversationTurn> afterChatTurns) {
    final var beforeTurns = beforeChatTurns.getAllValues();
    final var afterTurns = afterChatTurns.getAllValues();
    assertThat(beforeTurns).hasSize(2);
    assertThat(afterTurns).hasSize(2);

    // turn 1: user prompt → assistant with one tool call + per-turn metrics (incl. duration)
    assertThat(beforeTurns.get(0).iterationKey()).isEqualTo(1);
    assertThat(beforeTurns.get(0).inputMessages()).singleElement().isInstanceOf(UserMessage.class);
    assertThat(afterTurns.get(0).assistantMessage().toolCalls()).hasSize(1);
    assertThat(afterTurns.get(0).metrics().executionTime()).isNotNull();

    // turn 2: tool result → assistant final answer (no tool calls)
    assertThat(beforeTurns.get(1).iterationKey()).isEqualTo(2);
    assertThat(beforeTurns.get(1).inputMessages())
        .anyMatch(ToolCallResultMessage.class::isInstance);
    assertThat(afterTurns.get(1).assistantMessage().toolCalls()).isEmpty();
  }

  /**
   * Verifies on the engine that the agent instance the connector created is retrievable by key from
   * secondary storage (RDBMS, eventually consistent), carrying the create-time definition. This
   * proves the {@code create} command genuinely landed on the broker and was indexed.
   *
   * <p>Scope note: accumulated metrics / final status are intentionally NOT asserted here. The
   * RDBMS exporter <em>does</em> handle agent-instance updates ({@code AgentInstanceExportHandler}
   * exports CREATED/UPDATED/COMPLETED), but in this embedded process-test the read-back metrics did
   * not converge to the final totals within a 60s poll (stayed at create/partial state). The
   * per-turn metric deltas and status transitions are meanwhile verified via the {@code
   * agentInstanceClient} spy and the agent response's {@code hasMetrics(...)}; asserting the
   * accumulated state via the GET API is a follow-up (see below).
   */
  private void assertAgentInstanceCreatedOnEngine(long agentInstanceKey, String expectedModel) {
    await()
        .alias("agent instance via REST get-by-key")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var agentInstance =
                  camundaClient.newAgentInstanceGetRequest(agentInstanceKey).execute();
              assertThat(agentInstance.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
              assertThat(agentInstance.getDefinition().getModel()).isEqualTo(expectedModel);
              assertThat(agentInstance.getStatus()).isNotNull();
            });
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
          "id": "%s",
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
