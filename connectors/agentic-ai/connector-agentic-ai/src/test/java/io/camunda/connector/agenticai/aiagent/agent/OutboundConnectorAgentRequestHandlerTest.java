/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.CapabilityAwareToolCallResultStrategy;
import io.camunda.connector.agenticai.aiagent.framework.multimodal.ToolCallResultStrategy;
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
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundConnectorAgentRequestHandlerTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final String SYSTEM_PROMPT = "You are a helpful assistant. Be nice.";
  private static final Message SYSTEM_MESSAGE = systemMessage(SYSTEM_PROMPT);
  private static final Message USER_MESSAGE = userMessage("Write a haiku about the sea");
  private static final UserPromptConfiguration USER_PROMPT =
      new UserPromptConfiguration("Write a haiku about the sea", List.of());
  private static final Duration EXECUTION_TIME = Duration.ofMillis(123);

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentConversationTurnInputComposer agentInputComposer;
  @Mock private ChatModelApiRegistry chatModelApiRegistry;
  @Mock private ChatModelApi chatModelApi;
  @Mock private SystemPromptComposer systemPromptComposer;
  @Mock private AgentResponseHandler responseHandler;
  @Mock private AgentInstanceClient agentInstanceClient;
  @Mock private OutboundConnectorAgentExecutionContext agentExecutionContext;
  @Mock private ToolCallResultStrategy toolCallResultStrategy;

  @Captor private ArgumentCaptor<ChatModelRequest> chatModelRequestCaptor;
  @Captor private ArgumentCaptor<AgentConversationTurn> turnCaptor;
  @Captor private ArgumentCaptor<AgentInstanceUpdateRequest> agentInstanceUpdateRequestCaptor;

  @InjectMocks private OutboundConnectorAgentRequestHandler requestHandler;

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  @BeforeEach
  void setUp() {
    doReturn(new InProcessConversationStore())
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
    // identity by default (pre-existing tests carry no tool-result documents); the
    // strategy-focused test below overrides this with the real implementation
    lenient()
        .when(toolCallResultStrategy.apply(any(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private Document createDocument(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
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
    assertThat(response.agentResponse().context()).isEqualTo(discoveryAgentContext);
    assertThat(response.agentResponse().toolCalls())
        .containsExactly(ToolCallProcessVariable.from(toolDiscoveryToolCalls.getFirst()));

    verifyNoInteractions(agentInputComposer, chatModelApiRegistry, responseHandler);
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
    assertThat(response.agentResponse()).isNull();

    verifyNoInteractions(agentInputComposer, chatModelApiRegistry, responseHandler);
  }

  @Test
  void orchestratesRequestExecutionWithoutToolCalls() {
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessageText =
        "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
    final var assistantMessage = assistantMessage(assistantMessageText);
    mockFrameworkExecution(assistantMessage);

    final var expectedStoredMessages = List.of(SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // snapshot is captured before the assistant message is ingested
    assertThat(chatModelRequestCaptor.getValue().snapshot().messages())
        .containsExactly(SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(
            new AgentMetrics(
                1, TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(), 0));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedStoredMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isEqualTo(assistantMessageText);
    assertThat(agentResponse.toolCalls()).isEmpty();
  }

  @Test
  void orchestratesRequestExecutionWithToolCalls() {
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockFrameworkExecution(assistantMessage);

    final var expectedStoredMessages = List.of(SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(chatModelRequestCaptor.getValue().snapshot().messages())
        .containsExactly(SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(
            new AgentMetrics(
                1, TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(), 2));
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
  }

  @Test
  void throwsExceptionWhenInputComposerReturnsNoInput() {
    mockConfiguration();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NoInput());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("NO_USER_MESSAGE_CONTENT");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
            });

    verifyNoInteractions(chatModelApiRegistry, agentInstanceClient);
  }

  @Test
  void throwsWhenModelCallLimitReachedAfterRehydration() {
    // a conversation rehydrated from history: reconstructed turns carry empty metrics, so the
    // limit must be enforced against the durable cumulative counter on the agent context.
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                null,
                "model",
                "anthropic",
                null,
                USER_PROMPT,
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
    verifyNoInteractions(chatModelApiRegistry);
  }

  @Test
  void proceedsThroughContinuationRoundsAsSeparatePersistedTurns() {
    // a provider Continuation (e.g. Anthropic pause_turn) is ingested as its own persisted turn,
    // and the loop keeps calling the chat model until a Completed result ends it
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var doneMessage =
        assistantMessage(
            "Endless waves whisper | moonlight dances on the tide | secrets drift below.");
    final var continuationMetrics =
        new AgentMetrics(
            1, TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(), 0);
    final var completedMetrics =
        new AgentMetrics(1, TokenUsage.builder().inputTokenCount(5).outputTokenCount(8).build(), 0);

    doReturn(chatModelApi).when(chatModelApiRegistry).resolve(any());
    doReturn(new ChatModelResult.Continuation(partialMessage, continuationMetrics))
        .doReturn(new ChatModelResult.Completed(doneMessage, completedMetrics))
        .when(chatModelApi)
        .call(chatModelRequestCaptor.capture());

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModelApi, times(2)).call(any());

    final var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.responseMessage()).isEqualTo(doneMessage);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(
            new AgentMetrics(
                2, TokenUsage.builder().inputTokenCount(15).outputTokenCount(28).build(), 0));

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

    // intermediate continuation round pushes its own counters-only delta (no status change —
    // the agent stays THINKING mid-turn); the final round's push (with the real end status)
    // fires synchronously during handleRequest (task flavor always updates before completion)
    verify(agentInstanceClient, times(3))
        .update(eq(agentExecutionContext), any(), agentInstanceUpdateRequestCaptor.capture());
    final var updates = agentInstanceUpdateRequestCaptor.getAllValues();
    // [0] THINKING patch before the LLM call
    assertThat(updates.get(0).status()).isEqualTo(AgentInstanceUpdateStatus.THINKING);
    // [1] intermediate continuation round: counters-only delta, no status
    assertThat(updates.get(1).status()).isNull();
    assertThat(updates.get(1).delta()).isEqualTo(continuationMetrics);
    // [2] final round: synchronous end-of-turn push with real status + final round's metrics
    assertThat(updates.get(2).status()).isEqualTo(AgentInstanceUpdateStatus.IDLE);
    assertThat(updates.get(2).delta()).isEqualTo(completedMetrics);
  }

  @Test
  void intermediateContinuationMetricsPushFailureDoesNotAbortTheLoop() {
    // a telemetry PATCH must not fail a running agent (rethrowOnFailure=false) — even if the
    // intermediate push throws, the loop must still proceed to the next round and complete
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var doneMessage = assistantMessage("done");
    final var continuationMetrics = new AgentMetrics(1, TokenUsage.empty(), 0);
    final var completedMetrics = new AgentMetrics(1, TokenUsage.empty(), 0);

    doReturn(chatModelApi).when(chatModelApiRegistry).resolve(any());
    doReturn(new ChatModelResult.Continuation(partialMessage, continuationMetrics))
        .doReturn(new ChatModelResult.Completed(doneMessage, completedMetrics))
        .when(chatModelApi)
        .call(any());

    // first update() call is the THINKING patch (must succeed), second is the intermediate
    // continuation push (fails), remaining calls proceed normally
    doNothing()
        .doThrow(new RuntimeException("agent instance service unavailable"))
        .doNothing()
        .when(agentInstanceClient)
        .update(any(), any(), any());

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // loop still completed both rounds despite the failed intermediate push
    verify(chatModelApi, times(2)).call(any());
    assertThat(response.agentResponse()).isNotNull();
    assertThat(response.agentResponse().responseMessage()).isEqualTo(doneMessage);
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
                null,
                "model",
                "anthropic",
                null,
                USER_PROMPT,
                null,
                new LimitsConfiguration(1),
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var continuationMetrics = new AgentMetrics(1, TokenUsage.empty(), 0);

    doReturn(chatModelApi).when(chatModelApiRegistry).resolve(any());
    doReturn(new ChatModelResult.Continuation(partialMessage, continuationMetrics))
        .when(chatModelApi)
        .call(any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));

    // limit blocks the 2nd round — the chat model is called exactly once
    verify(chatModelApi, times(1)).call(any());
  }

  @Test
  void shouldEmitThinkingPatchThenMetricsPatchDuringHandleRequest() {
    // given
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = assistantMessage("No tool calls here.");
    mockFrameworkExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: THINKING patch first, then metrics+status patch — both emitted during handleRequest
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
                    .delta(
                        new AgentMetrics(
                            1,
                            TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(),
                            0))
                    .build()));
    verifyHistoryItemsCreated();
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldEmitThinkingPatchThenToolCallingMetricsPatchDuringHandleRequest() {
    // given
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockFrameworkExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: THINKING patch first, then metrics+status patch — both emitted during handleRequest
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
                    .status(AgentInstanceUpdateStatus.TOOL_CALLING)
                    .delta(
                        new AgentMetrics(
                            1,
                            TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(),
                            2))
                    .build()));
    verifyHistoryItemsCreated();
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldNotCountToolCallResultsInDeltaWhenLlmRespondsWithoutToolCalls() {
    // given: tool call results arrive as input, but the LLM responds with plain text (no new tool
    // calls)
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = assistantMessage("Done.");
    mockFrameworkExecution(assistantMessage);
    mockResponseHandler();

    // when
    requestHandler.handleRequest(agentExecutionContext);

    // then: toolCalls=0 in delta because the LLM emitted no tool calls
    verify(agentInstanceClient)
        .update(
            eq(agentExecutionContext),
            any(),
            eq(
                AgentInstanceUpdateRequest.builder()
                    .status(AgentInstanceUpdateStatus.IDLE)
                    .delta(
                        new AgentMetrics(
                            1,
                            TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(),
                            0))
                    .build()));
  }

  @Test
  void proceedAppliesToolCallResultStrategyBeforeModelCallAndPersistsSelfDescribingMessage() {
    // a tool-result document routed through the real strategy against bridge-like capabilities
    // ([TEXT] only for tool results) must be stripped from the outgoing snapshot and replaced by a
    // trailing synthetic <doc/> message, while the persisted conversation keeps the document
    // inside the ToolCallResultMessage (self-describing) with no synthetic message added.
    mockConfiguration();
    mockSystemPrompt();

    var pdf = createDocument("pdf-bytes", "application/pdf", "report.pdf");
    var base =
        ToolCallResultContent.from(
            ToolCallResult.builder()
                .id("call_1")
                .name("getReport")
                .content(Map.of("k", "v"))
                .build());
    var selfDescribingContent = new ArrayList<>(base.content());
    selfDescribingContent.add(DocumentContent.documentContent(pdf));
    var toolResultMessage =
        ToolCallResultMessage.builder()
            .results(List.of(base.withContent(selfDescribingContent)))
            .build();
    mockProceed(toolResultMessage);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    var bridgeCaps =
        ModelCapabilities.builder()
            .userMessageModalities(List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT))
            .toolResultModalities(List.of(Modality.TEXT))
            .assistantMessageModalities(List.of(Modality.TEXT))
            .build();
    when(chatModelApi.capabilities()).thenReturn(bridgeCaps);
    when(toolCallResultStrategy.apply(any(), any()))
        .thenAnswer(
            inv ->
                new CapabilityAwareToolCallResultStrategy()
                    .apply(inv.getArgument(0), inv.getArgument(1)));

    var assistantMessage = assistantMessage("ok");
    mockFrameworkExecution(assistantMessage);
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // sent-to-model snapshot: document stripped, trailing synthetic user message inserted
    var sentMessages = chatModelRequestCaptor.getValue().snapshot().messages();
    assertThat(sentMessages).hasSize(3);
    assertThat(sentMessages.get(0)).isEqualTo(SYSTEM_MESSAGE);
    var strippedTrm = (ToolCallResultMessage) sentMessages.get(1);
    assertThat(strippedTrm.results().getFirst().content())
        .noneMatch(DocumentContent.class::isInstance);
    var synthetic = (UserMessage) sentMessages.get(2);
    assertThat(synthetic.metadata()).containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    assertThat(synthetic.content()).contains(DocumentContent.documentContent(pdf));

    // persisted conversation: self-describing message unchanged (document still inline), no
    // synthetic message ever persisted
    var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
    var persistedMessages =
        ((InProcessConversationContext) agentResponse.context().conversation()).messages();
    assertThat(persistedMessages).hasSize(3);
    assertThat(persistedMessages.get(0)).isEqualTo(SYSTEM_MESSAGE);
    var persistedTrm = (ToolCallResultMessage) persistedMessages.get(1);
    assertThat(persistedTrm.results().getFirst().content())
        .anyMatch(DocumentContent.class::isInstance);
    assertThat(persistedMessages)
        .noneMatch(
            m ->
                m instanceof UserMessage um
                    && um.metadata() != null
                    && Boolean.TRUE.equals(
                        um.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS)));
  }

  private void mockConfiguration() {
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                null, "model", "anthropic", null, USER_PROMPT, null, null, null, null));
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

  private void verifyHistoryItemsCreated() {
    verify(agentInstanceClient)
        .createHistoryForInputMessages(eq(agentExecutionContext), any(), any(), any(), any());
    verify(agentInstanceClient)
        .createHistoryForAssistantMessage(eq(agentExecutionContext), any(), any(), any());
  }

  private void mockFrameworkExecution(AssistantMessage assistantMessage) {
    final var metrics =
        new AgentMetrics(
            1,
            TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(),
            assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size(),
            EXECUTION_TIME);
    doReturn(chatModelApi).when(chatModelApiRegistry).resolve(any());
    doReturn(new ChatModelResult.Completed(assistantMessage, metrics))
        .when(chatModelApi)
        .call(chatModelRequestCaptor.capture());
  }
}
