/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV1Function;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentSubProcessRequestHandlerTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final String SYSTEM_PROMPT = "You are a helpful assistant. Be nice.";
  private static final Message SYSTEM_MESSAGE = systemMessage(SYSTEM_PROMPT);
  private static final Message USER_MESSAGE = userMessage("Write a haiku about the sea");
  private static final Duration EXECUTION_TIME = Duration.ofMillis(123);

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentConversationTurnInputComposer agentInputComposer;
  @Mock private ChatModelRegistry chatModelRegistry;
  @Mock private ChatModel chatModel;
  @Mock private SystemPromptComposer systemPromptComposer;
  @Mock private AgentResponseHandler responseHandler;
  @Mock private AgentInstanceClient agentInstanceClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentSubProcessExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<ChatRequest> chatModelRequestCaptor;
  @Captor private ArgumentCaptor<AgentConversationTurn> turnCaptor;
  @Captor private ArgumentCaptor<AgentInstanceUpdateRequest> agentInstanceUpdateRequestCaptor;

  @InjectMocks private AgentSubProcessRequestHandler requestHandler;

  @BeforeEach
  void setUp() {
    ConversationStore conversationStore = spy(new InProcessConversationStore());
    doReturn(conversationStore)
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
    // configuration() returns a record that cannot be deep-stubbed; provide an explicit default
    lenient()
        .doReturn(
            new AgentConfiguration(
                new OpenAiProviderConfiguration(null),
                new PromptConfiguration.SystemPromptConfiguration(null),
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                null,
                null,
                null))
        .when(agentExecutionContext)
        .configuration();
    // avoid deep-stubbing a mock UserPromptConfiguration (these tests drive the turn via the
    // composer mock and don't rely on the user prompt)
    lenient().doReturn(null).when(agentExecutionContext).userPrompt();
  }

  @Test
  void dispatchesToolDiscoveryWhenInitializationReturnsDiscoverTools() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DiscoverTools(discoveryAgentContext, toolDiscoveryToolCalls));

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).containsOnlyKeys("agentContext", "toolCallResults");
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();
    assertThat(response.responseValue()).isNotNull();
    assertThat(response.responseValue().context()).isEqualTo(discoveryAgentContext);
    assertThat(response.responseValue().toolCalls())
        .containsExactly(ToolCallProcessVariable.from(toolDiscoveryToolCalls.getFirst()));

    verifyNoInteractions(agentInputComposer, chatModelRegistry, chatModel, responseHandler);
  }

  @Test
  void toolDiscoveryListenerPatchesStatusOnJobCompletion() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DiscoverTools(discoveryAgentContext, toolDiscoveryToolCalls));

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // no agentInstanceClient calls during handleRequest itself
    verifyNoInteractions(agentInstanceClient);

    // when: job completes — TOOL_DISCOVERY status patch fires
    response.onJobCompleted();
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            isNull(),
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.TOOL_DISCOVERY)));
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void toolDiscoveryListenerSkipsStatusPatchOnJobCompletionFailure() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DiscoverTools(discoveryAgentContext, toolDiscoveryToolCalls));

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verifyNoInteractions(agentInstanceClient);

    // when: job completion fails — listener logs and does nothing
    response.onJobCompletionFailed(
        new JobCompletionFailure.ExecutionFailed(new RuntimeException(), null));
    verifyNoInteractions(agentInstanceClient);
  }

  @Test
  void returnsNoOpResponseWhenInitializationReturnsDeferConversation() {
    reset(conversationStoreRegistry);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DeferConversation());

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.responseValue()).isNull();
    assertThat(response.completionConditionFulfilled()).isFalse();

    verifyNoInteractions(agentInputComposer, chatModelRegistry, chatModel, responseHandler);
  }

  @Test
  void orchestratesRequestExecutionWithoutToolCalls() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessageText =
        "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
    final var assistantMessage = assistantMessage(assistantMessageText);
    mockChatModelExecution(assistantMessage);

    final var expectedStoredMessages = List.of(SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).containsOnlyKeys("agentContext", "agent");
    assertThat(response.completionConditionFulfilled()).isTrue();
    assertThat(response.cancelRemainingInstances()).isFalse();

    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context()).isEqualTo(response.variables().get("agentContext"));
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20), 0));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedStoredMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isEqualTo(assistantMessageText);
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertThat(response.elementActivations()).isEmpty();

    // snapshot is captured before the assistant message is ingested
    assertThat(chatModelRequestCaptor.getValue().snapshot().messages())
        .containsExactly(SYSTEM_MESSAGE, USER_MESSAGE);
  }

  @Test
  void orchestratesRequestExecutionWithToolCalls() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);

    final var expectedStoredMessages = List.of(SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).containsOnlyKeys("agentContext", "toolCallResults");
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();

    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20), 2));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedStoredMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isNull();
    assertThat(agentResponse.toolCalls())
        .containsExactly(
            ToolCallProcessVariable.from(TOOL_CALLS.get(0)),
            ToolCallProcessVariable.from(TOOL_CALLS.get(1)));

    assertThat(response.elementActivations()).hasSize(2);
    assertThat(response.elementActivations().get(0).elementId()).isEqualTo("getWeather");
    assertThat(response.elementActivations().get(0).variables())
        .isEqualTo(
            Map.of(
                AgentSubProcessV1Function.TOOL_CALL_VARIABLE,
                agentResponse.toolCalls().get(0),
                AgentSubProcessV1Function.TOOL_CALL_RESULT_VARIABLE,
                ""));
    assertThat(response.elementActivations().get(1).elementId()).isEqualTo("getDateTime");
    assertThat(response.elementActivations().get(1).variables())
        .isEqualTo(
            Map.of(
                AgentSubProcessV1Function.TOOL_CALL_VARIABLE,
                agentResponse.toolCalls().get(1),
                AgentSubProcessV1Function.TOOL_CALL_RESULT_VARIABLE,
                ""));
  }

  @Test
  void orchestratesRequestExecutionWithInterruptedToolCall() {
    List<ToolCallResult> toolCallResults = List.of(TOOL_CALL_RESULTS.getFirst());
    mockSystemPrompt();

    final var interruptedMessage =
        toolCallResultMessage(
            toolCallResults.stream()
                .map(
                    tc ->
                        ToolCallResult.forCancelledToolCall(
                            tc.id(), tc.name(), OffsetDateTime.parse("2026-07-02T10:00:00Z")))
                .toList());
    mockProceed(interruptedMessage);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().build();
    mockChatModelExecution(assistantMessage);

    final var expectedStoredMessages =
        List.of(SYSTEM_MESSAGE, interruptedMessage, assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).containsOnlyKeys("agentContext", "agent");
    assertThat(response.completionConditionFulfilled()).isTrue();
    assertThat(response.cancelRemainingInstances()).isTrue();

    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context()).isEqualTo(response.variables().get("agentContext"));
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20), 0));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedStoredMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isNull();
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertThat(response.elementActivations()).isEmpty();
  }

  @Test
  void silentlyCompletesJobWhenInputComposerReturnsNoOp() {
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.Deferred());

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).isEmpty();
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();
    assertThat(response.elementActivations()).isEmpty();

    verifyNoInteractions(chatModelRegistry, chatModel);
  }

  @Test
  void silentlyCompletesJobWhenInputComposerReturnsNoInput() {
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NoInput());

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).isEmpty();
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();

    verifyNoInteractions(chatModelRegistry, chatModel);
  }

  @Test
  void shouldEmitOnlyThinkingPatchSynchronouslyAndDeferMetricsPatchOnToolCallTurn() {
    // given: LLM returns tool calls → intermediate turn → element instance stays alive after
    // completionConditionFulfilled=false → deferred PATCH is safe
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: only THINKING patch is synchronous; metrics PATCH is NOT sent yet
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.THINKING)
                    .tools(TOOL_DEFINITIONS)
                    .build()));
    verifyHistoryItemsCreated(assistantMessage);
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — deferred metrics PATCH fires now
    response.onJobCompleted();
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.TOOL_CALLING)
                    .delta(new AgentMetrics(1, new TokenUsage(10, 20), 2))
                    .build()));
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldEmitThinkingAndMetricsPatchSynchronouslyOnFinalTurn() {
    // given: LLM returns no tool calls → final turn → AHSP closes
    // (completionConditionFulfilled=true)
    // → element instance dies after job completion → synchronous PATCH required
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: THINKING + IDLE metrics PATCH both synchronous
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.THINKING)
                    .tools(TOOL_DEFINITIONS)
                    .build()));
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.IDLE)
                    .delta(new AgentMetrics(1, new TokenUsage(10, 20), 0))
                    .build()));
    verifyHistoryItemsCreated(assistantMessage);
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no deferred PATCH (was already sent synchronously)
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldReportMetricsWithoutToolCallsWhenJobCompletionFails() {
    // given: LLM returns tool calls → deferred path
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // only THINKING was synchronous
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.THINKING)
                    .tools(TOOL_DEFINITIONS)
                    .build()));
    verifyHistoryItemsCreated(assistantMessage);
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completion fails (execution error)
    // toolCalls are stripped to avoid inflating the counter for unactivated elements
    response.onJobCompletionFailed(
        new JobCompletionFailure.ExecutionFailed(new RuntimeException(), null));

    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.IDLE)
                    .delta(new AgentMetrics(1, new TokenUsage(10, 20), 0))
                    .build()));
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldReportMetricsWithoutToolCallsWhenJobSuperseded() {
    // given: LLM returns tool calls → deferred path
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // only THINKING was synchronous
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.THINKING)
                    .tools(TOOL_DEFINITIONS)
                    .build()));
    verifyHistoryItemsCreated(assistantMessage);
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job superseded (NOT_FOUND) — deferred listener fires, strips toolCalls, no status
    // change
    response.onJobCompletionFailed(
        new JobCompletionFailure.CommandFailure.CommandIgnored(new RuntimeException()));

    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .delta(new AgentMetrics(1, new TokenUsage(10, 20), 0))
                    .build()));
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void blankSystemPrompt_omitsSystemMessageFromConversation() {
    // a blank composed system prompt must not be sent to the LLM nor persisted as a message
    when(systemPromptComposer.compose(any(), any())).thenReturn("   ");
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = assistantMessage("hi");
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactly(USER_MESSAGE, assistantMessage));
    // no system message is sent to the LLM either
    assertThat(chatModelRequestCaptor.getValue().snapshot().messages())
        .containsExactly(USER_MESSAGE);
  }

  @Test
  void throwsWhenModelCallLimitReachedAfterRehydration() {
    // a multi-turn conversation rehydrated from history: reconstructed turns carry empty metrics,
    // so the limit must be enforced against the durable cumulative counter on the agent context.
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                null,
                null,
                new UserPromptConfiguration("user input", List.of()),
                null,
                new LimitsConfiguration(2),
                null,
                null));

    final var contextAtLimit =
        AgentContext.builder()
            .state(AgentState.READY)
            .toolDefinitions(TOOL_DEFINITIONS)
            .metrics(new AgentMetrics(2, TokenUsage.empty(), 0))
            .build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(contextAtLimit, List.of()));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));

    // limit is checked before the LLM call — no chat request is issued
    verifyNoInteractions(chatModelRegistry, chatModel);
  }

  @Test
  void throwsWhenModelResponseIsContentFilteredBeforeIngestOrHistoryWrite() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var filteredAssistantMessage =
        AssistantMessage.builder().stopReason(StopReason.CONTENT_FILTERED).build();
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenReturn(new ChatResult.Completed(filteredAssistantMessage, AgentMetrics.empty()));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED));

    // the guard fires before ingest / history write: THINKING status + input-message history are
    // sent before the model call, but the assistant-message history write and response handling
    // never happen
    verify(agentInstanceClient, never())
        .createHistoryForAssistantMessage(any(), any(), any(), any());
    verifyNoInteractions(responseHandler);
  }

  @Test
  void proceedsThroughContinuationRoundsAsSeparatePersistedTurns() {
    // a provider Continuation (e.g. Anthropic pause_turn) is ingested as its own persisted turn,
    // and the loop keeps calling the chat model until a Completed result ends it
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var doneMessage =
        assistantMessage(
            "Endless waves whisper | moonlight dances on the tide | secrets drift below.");
    final var continuationMetrics = new AgentMetrics(1, new TokenUsage(10, 20), 0);
    final var completedMetrics = new AgentMetrics(1, new TokenUsage(5, 8), 0);

    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Continuation(partialMessage, continuationMetrics))
        .doReturn(new ChatResult.Completed(doneMessage, completedMetrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());

    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.responseMessage()).isEqualTo(doneMessage);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(2, new TokenUsage(15, 28), 0));

    verify(agentInstanceClient, times(2))
        .createHistoryForAssistantMessage(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any());
    assertThat(turnCaptor.getAllValues())
        .extracting(AgentConversationTurn::assistantMessage)
        .containsExactly(partialMessage, doneMessage);

    // createHistoryForInputMessages is only called once, at the top of proceed() — continuation
    // rounds carry no new input
    verify(agentInstanceClient)
        .createHistoryForInputMessages(eq(agentExecutionContext), any(), any(), any(), any());

    // exactly one metrics push per job invocation, at the completion barrier — its delta covers
    // the whole invocation (all continuation rounds summed), while its status reflects the final
    // round
    verify(agentInstanceClient, times(2))
        .update(eq(agentExecutionContext), any(), agentInstanceUpdateRequestCaptor.capture());
    final var updates = agentInstanceUpdateRequestCaptor.getAllValues();
    // [0] THINKING patch before the LLM call
    assertThat(updates.get(0).status()).isEqualTo(AgentInstanceUpdateStatus.THINKING);
    // [1] final round: synchronous end-of-turn push with real status + the whole invocation's
    // summed metrics
    assertThat(updates.get(1).status()).isEqualTo(AgentInstanceUpdateStatus.IDLE);
    assertThat(updates.get(1).delta()).isEqualTo(continuationMetrics.add(completedMetrics));
  }

  @Test
  void throwsWhenModelCallLimitReachedBetweenContinuationRounds() {
    // the limit is re-checked before starting the next round: hitting the cap after a Continuation
    // blocks the next call, it does not abort the round just completed
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                new OpenAiProviderConfiguration(null),
                new PromptConfiguration.SystemPromptConfiguration(null),
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                new LimitsConfiguration(1),
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var continuationMetrics = new AgentMetrics(1, TokenUsage.empty(), 0);

    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Continuation(partialMessage, continuationMetrics))
        .when(chatModel)
        .execute(any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));

    // limit blocks the 2nd round — the chat model is called exactly once
    verify(chatModel, times(1)).execute(any());
  }

  private void mockSystemPrompt() {
    when(systemPromptComposer.compose(any(), any())).thenReturn(SYSTEM_PROMPT);
  }

  private void mockProceed(Message... inputMessages) {
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(inputMessages)));
  }

  private void mockResponseHandler() {
    when(responseHandler.createResponse(any(AgentConversation.class)))
        .thenAnswer(
            i -> {
              final var conversation = i.getArgument(0, AgentConversation.class);
              final var assistantMessage = conversation.lastTurn().orElseThrow().assistantMessage();
              final var toolCalls =
                  assistantMessage.toolCalls() == null
                      ? List.<ToolCallProcessVariable>of()
                      : assistantMessage.toolCalls().stream()
                          .map(ToolCallProcessVariable::from)
                          .toList();
              return AgentResponse.builder()
                  .context(conversation.toAgentContext())
                  .responseMessage(assistantMessage)
                  .responseText(assistantMessage.hasToolCalls() ? null : textOf(assistantMessage))
                  .toolCalls(toolCalls)
                  .build();
            });
  }

  private static String textOf(AssistantMessage assistantMessage) {
    if (assistantMessage.content() == null) {
      return null;
    }
    return assistantMessage.content().stream()
        .filter(TextContent.class::isInstance)
        .map(c -> ((TextContent) c).text())
        .findFirst()
        .orElse(null);
  }

  private void verifyHistoryItemsCreated(AssistantMessage expectedAssistantMessage) {
    verify(agentInstanceClient)
        .createHistoryForInputMessages(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any(), any());
    assertThat(turnCaptor.getValue().inputMessages()).containsExactly(USER_MESSAGE);

    verify(agentInstanceClient)
        .createHistoryForAssistantMessage(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any());
    assertThat(turnCaptor.getValue().assistantMessage()).isEqualTo(expectedAssistantMessage);
  }

  private void mockChatModelExecution(AssistantMessage assistantMessage) {
    final var metrics =
        new AgentMetrics(
            1,
            new TokenUsage(10, 20),
            assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size(),
            EXECUTION_TIME);
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(chatModelRequestCaptor.capture()))
        .thenReturn(new ChatResult.Completed(assistantMessage, metrics));
  }
}
