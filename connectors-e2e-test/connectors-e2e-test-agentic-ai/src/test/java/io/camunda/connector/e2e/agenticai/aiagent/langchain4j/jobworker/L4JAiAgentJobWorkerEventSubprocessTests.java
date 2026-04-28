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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Event-handling regression coverage for the agentic-AI sub-process flavor. Tests are designed to
 * pass on Camunda 8.9.0 (correct semantics) and fail on 8.9.1 — the version where inner-instance
 * variables started leaking out of ad-hoc sub-process activations (<a
 * href="https://github.com/camunda/camunda/issues/51939">camunda/camunda#51939</a>).
 *
 * <p>Two complementary detection signals are asserted in each scenario:
 *
 * <ol>
 *   <li>{@link #assertNoToolCallVariableLeak(ZeebeTest)} — direct check that {@code toolCall} did
 *       not bubble to the AHSP/root scope.
 *   <li>Strict chat-request assertion — leaked {@code (null, null)} entries that the agent's
 *       null-tolerant path would turn into synthetic {@code UserMessage}s would produce extra
 *       entries in the conversation that the recursive comparison rejects.
 * </ol>
 *
 * <p>The pending tool is modeled as a service task. The job is left unhandled (no worker is
 * registered) so the AHSP stays open while the test publishes the event message. The test then
 * either completes the job via {@link CamundaProcessTestContext#completeJob} or leaves it for the
 * engine to cancel (interrupt scenario).
 */
@SlowTest
public class L4JAiAgentJobWorkerEventSubprocessTests extends BaseL4JAiAgentJobWorkerTest {

  @Value("classpath:agentic-ai-ahsp-event-handling.bpmn")
  private Resource testProcessWithEventHandling;

  @Autowired private CamundaProcessTestContext processTestContext;

  private static final String EVENT_MESSAGE_NAME = "ai-agent-event";
  private static final String EVENT_PAYLOAD = "Stop using tools and answer immediately.";

  private static final String INITIAL_USER_PROMPT = "Explore some of your tools!";
  private static final String FINAL_AI_RESPONSE = "Alright, I will stop and respond directly.";
  private static final String FAST_TOOL_RESULT = "42";
  private static final String PENDING_TOOL_RESULT = "Pending tool completed externally.";
  private static final String FAST_TOOL_CALL_ID = "fast111";
  private static final String PENDING_TOOL_CALL_ID = "pending222";

  private static final String EVENT_CONTENT_EMPTY_WAIT =
      "An event was triggered but no content was returned."
          + " Execution waited for all in-flight tool executions to complete before proceeding.";
  private static final String CANCELLED_TOOL_RESULT = "Tool execution was canceled.";

  private static final SystemMessage SYSTEM_MESSAGE =
      new SystemMessage(
          "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking.");
  private static final UserMessage INITIAL_USER_MESSAGE = new UserMessage(INITIAL_USER_PROMPT);
  private static final AiMessage AI_FINAL_MESSAGE = new AiMessage(FINAL_AI_RESPONSE);

  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);

  /** Re-rolled per test method to keep message correlations isolated. */
  private String eventCorrelationKey;

  @BeforeEach
  void rollCorrelationKey() {
    eventCorrelationKey = "ai-agent-event-correlation-" + UUID.randomUUID();
  }

  /**
   * Event message is buffered before the process is started. When the AHSP enters and creates the
   * message subscription, the buffered message correlates immediately and the event SP fires —
   * usually before the agent's first job is processed, in which case the event UserMessage is
   * inserted next to the initial user prompt (iteration 1) rather than after the tool results
   * (iteration 2).
   */
  @Test
  void eventBeforeProcessActivation_withPayload() throws Exception {
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            new UserMessage(EVENT_PAYLOAD),
            aiToolCallMessage,
            new ToolExecutionResultMessage(FAST_TOOL_CALL_ID, "Fast_Tool", FAST_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, "Pending_Tool", PENDING_TOOL_RESULT),
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE)));

    publishEventMessage(EVENT_PAYLOAD);
    final var zeebeTest = startProcessInstance(e -> e);

    awaitPendingToolJobCreated(zeebeTest);
    awaitEventSubprocessCompleted(zeebeTest);
    completePendingToolJob(PENDING_TOOL_RESULT);

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);
    assertNoToolCallVariableLeak(zeebeTest);
  }

  /** Default {@code WAIT_FOR_TOOL_CALL_RESULTS}: agent waits for all tools before proceeding. */
  @Test
  void eventDuringToolExecution_waitForToolCallResults_withPayload() throws Exception {
    runEventDuringExecution(EVENT_PAYLOAD, eventUserMessage(EVENT_PAYLOAD), PENDING_TOOL_RESULT);
  }

  /**
   * Empty event payload — agent inserts the synthetic {@code
   * EVENT_CONTENT_EMPTY_WAIT_FOR_TOOL_CALL_RESULTS} placeholder UserMessage.
   */
  @Test
  void eventDuringToolExecution_emptyPayload() throws Exception {
    runEventDuringExecution("", eventUserMessage(EVENT_CONTENT_EMPTY_WAIT), PENDING_TOOL_RESULT);
  }

  /**
   * {@code INTERRUPT_TOOL_CALLS} (label "Cancel tool calls"): when the event arrives while a tool
   * is still in flight, the agent emits a synthetic cancelled result for the missing tool and
   * proceeds. The engine cancels the pending {@code Pending_Tool} as part of AHSP completion — the
   * test does not complete the captured job.
   */
  @Test
  void eventDuringToolExecution_cancelToolCalls_withPayload() throws Exception {
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(FAST_TOOL_CALL_ID, "Fast_Tool", FAST_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, "Pending_Tool", CANCELLED_TOOL_RESULT),
            new UserMessage(EVENT_PAYLOAD),
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE)));

    final var zeebeTest =
        startProcessInstance(e -> e.property("data.events.behavior", "INTERRUPT_TOOL_CALLS"));

    awaitPendingToolJobCreated(zeebeTest);
    publishEventMessage(EVENT_PAYLOAD);

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);
    assertNoToolCallVariableLeak(zeebeTest);
  }

  /**
   * No event published. Pure tool execution, full AHSP completion. Sole purpose: confirm the
   * inner-instance variable scoping does not leak in the absence of any event sub-process activity.
   */
  @Test
  void noEvent_noLeak() throws Exception {
    final var aiToolCallMessage =
        new AiMessage(
            "Calling the fast tool.", List.of(toolCall(FAST_TOOL_CALL_ID, "Fast_Tool", "{}")));

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(FAST_TOOL_CALL_ID, "Fast_Tool", FAST_TOOL_RESULT),
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE)));

    final var zeebeTest = startProcessInstance(e -> e);

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);
    assertNoToolCallVariableLeak(zeebeTest);
  }

  // ---- shared scenario for the wait-for-tool-results "during execution" variants ----

  private void runEventDuringExecution(
      String publishedPayload, ChatMessage expectedEventMessage, String pendingToolResultValue)
      throws Exception {
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(FAST_TOOL_CALL_ID, "Fast_Tool", FAST_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, "Pending_Tool", pendingToolResultValue),
            expectedEventMessage,
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE)));

    final var zeebeTest = startProcessInstance(e -> e);

    awaitPendingToolJobCreated(zeebeTest);
    publishEventMessage(publishedPayload);
    awaitEventSubprocessCompleted(zeebeTest);
    completePendingToolJob(pendingToolResultValue);

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);
    assertNoToolCallVariableLeak(zeebeTest);
  }

  // ---- helpers ------------------------------------------------------------

  private ZeebeTest startProcessInstance(Function<ElementTemplate, ElementTemplate> modifier)
      throws IOException {
    return createProcessInstance(
        testProcessWithEventHandling,
        modifier,
        Map.of("userPrompt", INITIAL_USER_PROMPT, "eventCorrelationKey", eventCorrelationKey));
  }

  private AiMessage aiTwoToolCalls() {
    return new AiMessage(
        "Calling both tools.",
        List.of(
            toolCall(FAST_TOOL_CALL_ID, "Fast_Tool", "{}"),
            toolCall(PENDING_TOOL_CALL_ID, "Pending_Tool", "{}")));
  }

  private ToolExecutionRequest toolCall(String id, String name, String arguments) {
    return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
  }

  private ChatResponse toolExecutionResponse(AiMessage aiMessage) {
    return ChatResponse.builder()
        .metadata(
            ChatResponseMetadata.builder()
                .finishReason(FinishReason.TOOL_EXECUTION)
                .tokenUsage(new TokenUsage(10, 20))
                .build())
        .aiMessage(aiMessage)
        .build();
  }

  private ChatResponse stopResponse(AiMessage aiMessage) {
    return ChatResponse.builder()
        .metadata(
            ChatResponseMetadata.builder()
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(100, 200))
                .build())
        .aiMessage(aiMessage)
        .build();
  }

  private void publishEventMessage(String payload) {
    camundaClient
        .newPublishMessageCommand()
        .messageName(EVENT_MESSAGE_NAME)
        .correlationKey(eventCorrelationKey)
        .variable("eventPayload", payload)
        .timeToLive(Duration.ofSeconds(30))
        .send()
        .join();
  }

  private UserMessage eventUserMessage(String content) {
    return new UserMessage(content);
  }

  private void awaitPendingToolJobCreated(ZeebeTest zeebeTest) {
    final long processInstanceKey = zeebeTest.getProcessInstanceEvent().getProcessInstanceKey();

    await()
        .alias("Pending_Tool job created")
        .atMost(POLL_TIMEOUT)
        .untilAsserted(
            () -> {
              final var jobs =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(
                          f -> f.processInstanceKey(processInstanceKey).elementId("Pending_Tool"))
                      .send()
                      .join()
                      .items();
              assertThat(jobs).isNotEmpty();
            });
  }

  private void awaitEventSubprocessCompleted(ZeebeTest zeebeTest) {
    final long processInstanceKey = zeebeTest.getProcessInstanceEvent().getProcessInstanceKey();

    await()
        .alias("Event_Script element completed")
        .atMost(POLL_TIMEOUT)
        .untilAsserted(
            () -> {
              final var instances =
                  camundaClient
                      .newElementInstanceSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(processInstanceKey)
                                  .elementId("Event_Script")
                                  .state(ElementInstanceState.COMPLETED))
                      .send()
                      .join()
                      .items();
              assertThat(instances).isNotEmpty();
            });
  }

  private void completePendingToolJob(String toolCallResultValue) {
    processTestContext.completeJob(
        JobSelectors.byElementId("Pending_Tool"), Map.of("toolCallResult", toolCallResultValue));
  }
}
