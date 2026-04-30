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
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
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
 * End-to-end coverage of the agentic-AI event-handling contract: non-interrupting message-triggered
 * event sub-processes inside the AI Agent ad-hoc sub-process (AHSP). All scenarios run on a single
 * BPMN — {@code agentic-ai-ahsp-connectors-event.bpmn} — which mirrors the realistic connector tool
 * setup (SuperfluxProduct, Search_The_Web, etc.) with a user-feedback loop, plus a {@code
 * Pending_Tool} service task whose job tests intentionally hold to keep the AHSP open while events
 * are published.
 *
 * <p>Scenarios validated:
 *
 * <ul>
 *   <li>Event message published <em>before</em> AHSP activation (buffered, correlates on entry).
 *   <li>Event during in-flight tool execution with {@code WAIT_FOR_TOOL_CALL_RESULTS} (default).
 *   <li>Event during tool execution with {@code INTERRUPT_TOOL_CALLS} ("Cancel tool calls").
 *   <li>Event with empty payload — agent inserts a synthetic placeholder UserMessage (one variant
 *       per behavior, since the synthetic message text differs).
 *   <li>Multiple events on a single AHSP iteration — order preserved.
 * </ul>
 *
 * <p>Each scenario asserts the expected chat conversation, agent metrics and response text, the
 * user-feedback worker firing exactly once, and the inner-instance scoping invariant via {@link
 * #assertNoToolCallVariableLeak(ZeebeTest)} — the direct check for the regression introduced in
 * 8.9.1 (<a href="https://github.com/camunda/camunda/issues/51939">camunda/camunda#51939</a>).
 *
 * <p>For "during execution" scenarios the {@code Pending_Tool} job is left unhandled (no worker
 * registered). The test publishes the event message while the job is waiting, then either completes
 * the job via {@link CamundaProcessTestContext#completeJob} ({@code WAIT_FOR_TOOL_CALL_RESULTS}) or
 * leaves it for the engine to cancel ({@code INTERRUPT_TOOL_CALLS}).
 */
@SlowTest
public class L4JAiAgentJobWorkerEventsTests extends BaseL4JAiAgentJobWorkerTest {

  @Value("classpath:agentic-ai-ahsp-connectors-event.bpmn")
  private Resource testProcessWithEvents;

  @Autowired private CamundaProcessTestContext processTestContext;

  private static final String EVENT_MESSAGE_NAME = "ai-agent-message";
  private static final String EVENT_PAYLOAD = "Stop using tools and answer immediately.";

  private static final String INITIAL_USER_PROMPT = "Explore some of your tools!";
  private static final String FINAL_AI_RESPONSE = "Alright, I will stop and respond directly.";
  private static final String SUPERFLUX_TOOL_NAME = "SuperfluxProduct";
  private static final String SUPERFLUX_TOOL_ARGS = "{\"a\": 5, \"b\": 3}";
  private static final String SUPERFLUX_TOOL_RESULT = "24";
  private static final String SUPERFLUX_TOOL_CALL_ID = "superflux111";
  private static final String PENDING_TOOL_NAME = "Pending_Tool";
  private static final String PENDING_TOOL_RESULT = "Pending tool completed externally.";
  private static final String PENDING_TOOL_CALL_ID = "pending222";

  private static final String EVENT_CONTENT_EMPTY_WAIT =
      "An event was triggered but no content was returned."
          + " Execution waited for all in-flight tool executions to complete before proceeding.";
  private static final String EVENT_CONTENT_EMPTY_INTERRUPT =
      "An event was triggered but no content was returned."
          + " All in-flight tool executions were canceled.";
  private static final String CANCELLED_TOOL_RESULT = "Tool execution was canceled.";

  private static final SystemMessage SYSTEM_MESSAGE =
      new SystemMessage(
          "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking.");
  private static final UserMessage INITIAL_USER_MESSAGE = new UserMessage(INITIAL_USER_PROMPT);
  private static final AiMessage AI_FINAL_MESSAGE = new AiMessage(FINAL_AI_RESPONSE);

  private static final AgentMetrics EXPECTED_TWO_ITERATION_METRICS =
      new AgentMetrics(2, new AgentMetrics.TokenUsage(110, 220));

  /** Re-rolled per test method to keep message correlations isolated. */
  private String eventCorrelationKey;

  @BeforeEach
  void rollCorrelationKey() {
    eventCorrelationKey = "ai-agent-event-correlation-" + UUID.randomUUID();
  }

  /**
   * Event message is buffered before the process is started. When the AHSP enters and creates the
   * message subscription, the buffered message correlates immediately and the event SP fires —
   * usually before the agent's first chat call, so the event UserMessage lands next to the initial
   * user prompt rather than after the tool results.
   */
  @Test
  void eventBeforeProcessActivation_withPayload() throws Exception {
    final var aiToolCallMessage = aiSuperfluxToolCall();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            new UserMessage(EVENT_PAYLOAD),
            aiToolCallMessage,
            new ToolExecutionResultMessage(
                SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_RESULT),
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE), userSatisfiedFeedback()));

    publishEventMessage(EVENT_PAYLOAD);
    final var zeebeTest = startProcessInstance(e -> e);

    zeebeTest.waitForProcessCompletion();

    assertCompleted(zeebeTest, expectedConversation);
  }

  /** Default {@code WAIT_FOR_TOOL_CALL_RESULTS}: agent waits for all tools before proceeding. */
  @Test
  void eventDuringToolExecution_waitForToolCallResults_withPayload() throws Exception {
    runEventDuringExecution(EVENT_PAYLOAD, new UserMessage(EVENT_PAYLOAD), PENDING_TOOL_RESULT);
  }

  /**
   * Empty event payload with {@code WAIT_FOR_TOOL_CALL_RESULTS}: agent inserts the synthetic
   * placeholder UserMessage that ends with " Execution waited for all in-flight tool executions to
   * complete before proceeding."
   */
  @Test
  void eventDuringToolExecution_emptyPayload() throws Exception {
    runEventDuringExecution("", new UserMessage(EVENT_CONTENT_EMPTY_WAIT), PENDING_TOOL_RESULT);
  }

  /**
   * {@code INTERRUPT_TOOL_CALLS} (label "Cancel tool calls"): when the event arrives while a tool
   * is still in flight, the agent emits a synthetic cancelled result for the missing tool and
   * proceeds. The engine cancels the pending {@code Pending_Tool} as part of AHSP completion — the
   * test does not complete the captured job.
   */
  @Test
  void eventDuringToolExecution_cancelToolCalls_withPayload() throws Exception {
    runEventDuringExecutionWithCancel(EVENT_PAYLOAD, new UserMessage(EVENT_PAYLOAD));
  }

  /**
   * {@code INTERRUPT_TOOL_CALLS} with an empty payload: the agent inserts the synthetic placeholder
   * UserMessage that ends with " All in-flight tool executions were canceled." (the
   * interrupt-specific variant of {@code EVENT_CONTENT_EMPTY}).
   */
  @Test
  void eventDuringToolExecution_cancelToolCalls_emptyPayload() throws Exception {
    runEventDuringExecutionWithCancel("", new UserMessage(EVENT_CONTENT_EMPTY_INTERRUPT));
  }

  /**
   * Two events arrive on the same subscription during a single AHSP iteration. The agent partitions
   * {@code toolCallResults} into id-bearing tool results and id-less event entries ({@code
   * AgentMessagesHandlerImpl#addUserMessages}); each event becomes its own UserMessage and the
   * published order must be preserved in the chat request.
   */
  @Test
  void multipleEventsDuringToolExecution_preservesOrder() throws Exception {
    final var firstEventPayload = "First event arrived.";
    final var secondEventPayload = "Second event arrived.";
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(
                SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, PENDING_TOOL_NAME, PENDING_TOOL_RESULT),
            new UserMessage(firstEventPayload),
            new UserMessage(secondEventPayload),
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE), userSatisfiedFeedback()));

    final var zeebeTest = startProcessInstance(e -> e);

    awaitPendingToolJobCreated(zeebeTest);

    publishEventMessage(firstEventPayload);
    awaitEventSubprocessCompletions(zeebeTest, 1);
    publishEventMessage(secondEventPayload);
    awaitEventSubprocessCompletions(zeebeTest, 2);

    completePendingToolJob(PENDING_TOOL_RESULT);

    zeebeTest.waitForProcessCompletion();

    assertCompleted(zeebeTest, expectedConversation);
  }

  // ---- shared scenarios for the "during execution" variants ---------------

  private void runEventDuringExecution(
      String publishedPayload, ChatMessage expectedEventMessage, String pendingToolResultValue)
      throws Exception {
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(
                SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, PENDING_TOOL_NAME, pendingToolResultValue),
            expectedEventMessage,
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE), userSatisfiedFeedback()));

    final var zeebeTest = startProcessInstance(e -> e);

    awaitPendingToolJobCreated(zeebeTest);
    publishEventMessage(publishedPayload);
    awaitEventSubprocessCompletions(zeebeTest, 1);
    completePendingToolJob(pendingToolResultValue);

    zeebeTest.waitForProcessCompletion();

    assertCompleted(zeebeTest, expectedConversation);
  }

  private void runEventDuringExecutionWithCancel(
      String publishedPayload, ChatMessage expectedEventMessage) throws Exception {
    final var aiToolCallMessage = aiTwoToolCalls();

    final var expectedConversation =
        List.of(
            SYSTEM_MESSAGE,
            INITIAL_USER_MESSAGE,
            aiToolCallMessage,
            new ToolExecutionResultMessage(
                SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_RESULT),
            new ToolExecutionResultMessage(
                PENDING_TOOL_CALL_ID, PENDING_TOOL_NAME, CANCELLED_TOOL_RESULT),
            expectedEventMessage,
            AI_FINAL_MESSAGE);

    mockChatInteractions(
        ChatInteraction.of(toolExecutionResponse(aiToolCallMessage)),
        ChatInteraction.of(stopResponse(AI_FINAL_MESSAGE), userSatisfiedFeedback()));

    final var zeebeTest =
        startProcessInstance(e -> e.property("data.events.behavior", "INTERRUPT_TOOL_CALLS"));

    awaitPendingToolJobCreated(zeebeTest);
    publishEventMessage(publishedPayload);

    zeebeTest.waitForProcessCompletion();

    assertCompleted(zeebeTest, expectedConversation);
  }

  // ---- helpers ------------------------------------------------------------

  private ZeebeTest startProcessInstance(Function<ElementTemplate, ElementTemplate> modifier)
      throws IOException {
    return createProcessInstance(
        testProcessWithEvents,
        modifier,
        Map.of("userPrompt", INITIAL_USER_PROMPT, "eventCorrelationKey", eventCorrelationKey));
  }

  private void assertCompleted(ZeebeTest zeebeTest, List<ChatMessage> expectedConversation) {
    assertLastChatRequest(expectedConversation, false);
    assertReadyAgentResponse(zeebeTest, EXPECTED_TWO_ITERATION_METRICS);
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
    assertNoToolCallVariableLeak(zeebeTest);
  }

  private void assertReadyAgentResponse(ZeebeTest zeebeTest, AgentMetrics expectedMetrics) {
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(expectedMetrics)
                .hasResponseMessageText(FINAL_AI_RESPONSE)
                .hasResponseText(FINAL_AI_RESPONSE));
  }

  private AiMessage aiSuperfluxToolCall() {
    return new AiMessage(
        "I will use the superflux tool.",
        List.of(toolCall(SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_ARGS)));
  }

  private AiMessage aiTwoToolCalls() {
    return new AiMessage(
        "Calling the superflux and pending tools.",
        List.of(
            toolCall(SUPERFLUX_TOOL_CALL_ID, SUPERFLUX_TOOL_NAME, SUPERFLUX_TOOL_ARGS),
            toolCall(PENDING_TOOL_CALL_ID, PENDING_TOOL_NAME, "{}")));
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

  private void awaitPendingToolJobCreated(ZeebeTest zeebeTest) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasActiveElements(PENDING_TOOL_NAME);
  }

  private void awaitEventSubprocessCompletions(ZeebeTest zeebeTest, int expectedCount) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElement("Event_Script", expectedCount);
  }

  private void completePendingToolJob(String toolCallResultValue) {
    processTestContext.completeJob(
        JobSelectors.byElementId(PENDING_TOOL_NAME), Map.of("toolCallResult", toolCallResultValue));
  }
}
