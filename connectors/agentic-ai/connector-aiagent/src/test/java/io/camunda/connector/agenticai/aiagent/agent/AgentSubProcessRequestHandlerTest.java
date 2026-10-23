/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.testutil.MessageAssertions.assertMessagesEqualIgnoringSystemMessageId;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.AgentProcessVariables;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentSubProcessRequestHandlerTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder()
          .features(AgentFeatures.ofNewInstance())
          .state(AgentState.READY)
          .toolDefinitions(TOOL_DEFINITIONS)
          .build();

  private static final AgentContext CONTEXT_WITH_DOCUMENTS =
      INITIAL_AGENT_CONTEXT.withFeatures(
          List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));

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
  @Mock private ResponseSchemaValidator responseSchemaValidator;

  /** The document a runtime tool of the test returns without the agent knowing it before. */
  private static final Document NEW_REPORT = createDocument("new report", "new-report.txt");

  private static final RuntimeTool CREATE_REPORT_TOOL =
      StubRuntimeTool.of(
          "acme_create_report",
          (call, context) ->
              Boolean.TRUE.equals(call.arguments().get("fail"))
                  ? RuntimeToolResult.error("The report could not be created.")
                  : RuntimeToolResult.success(
                      List.of(
                          TextContent.textContent("Created the report."),
                          DocumentContent.documentContent(NEW_REPORT))));

  /** A tool that tells how many documents the agent knows when the call starts. */
  private static final RuntimeTool COUNT_DOCUMENTS_TOOL =
      StubRuntimeTool.of(
          "acme_count_documents",
          (call, context) ->
              RuntimeToolResult.success("documents: " + context.documents().all().size()));

  private static final RuntimeTool LOOKUP_TOOL =
      StubRuntimeTool.of(
          "acme_lookup", (call, context) -> RuntimeToolResult.success("found the customer"));

  private static final RuntimeTool HIDDEN_TOOL =
      StubRuntimeTool.of("acme_hidden", (call, context) -> RuntimeToolResult.success("hidden"))
          .enabledWhen(context -> false);

  private static final AtomicBoolean TOGGLE_ENABLED = new AtomicBoolean(true);

  /** A tool that can be disabled while the agent runs. */
  private static final RuntimeTool TOGGLE_TOOL =
      StubRuntimeTool.of("acme_toggle", (call, context) -> RuntimeToolResult.success("toggled"))
          .enabledWhen(context -> TOGGLE_ENABLED.get());

  @Spy
  private RuntimeToolRegistry runtimeToolRegistry =
      new RuntimeToolRegistryImpl(
          List.of(LOOKUP_TOOL, HIDDEN_TOOL, TOGGLE_TOOL, CREATE_REPORT_TOOL, COUNT_DOCUMENTS_TOOL),
          TestObjectMapperSupplier.INSTANCE);

  @Spy
  private RuntimeToolExecutor runtimeToolExecutor =
      new RuntimeToolExecutorImpl(runtimeToolRegistry, TestObjectMapperSupplier.INSTANCE);

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentSubProcessExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<ChatRequest> chatModelRequestCaptor;
  @Captor private ArgumentCaptor<AgentConversationTurn> turnCaptor;

  @InjectMocks private AgentSubProcessRequestHandler requestHandler;

  @BeforeEach
  void setUp() {
    TOGGLE_ENABLED.set(true);
    ConversationStore conversationStore = spy(new InProcessConversationStore());
    doReturn(conversationStore)
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
    // configuration() returns a record that cannot be deep-stubbed; provide an explicit default
    lenient()
        .doReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
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
        AgentContext.builder()
            .features(AgentFeatures.ofNewInstance())
            .state(AgentState.TOOL_DISCOVERY)
            .build();

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

    verifyNoInteractions(
        agentInputComposer, chatModelRegistry, chatModel, responseHandler, responseSchemaValidator);
  }

  @Test
  void dispatchesToolDiscoveryStatusUpdateDuringHandleRequest() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder()
            .features(AgentFeatures.ofNewInstance())
            .state(AgentState.TOOL_DISCOVERY)
            .build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DiscoverTools(discoveryAgentContext, toolDiscoveryToolCalls));

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // status update fires synchronously during handleRequest -- no completion listener involved
    verify(agentInstanceClient).applyToolDiscoveryStart(eq(agentExecutionContext), isNull());
    verifyNoMoreInteractions(agentInstanceClient);

    // job completion triggers no further agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void returnsNoOpResponseWhenInitializationReturnsDeferConversation() {
    reset(conversationStoreRegistry);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new DeferConversation());

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.responseValue()).isNull();
    assertThat(response.completionConditionFulfilled()).isFalse();

    verifyNoInteractions(
        agentInputComposer, chatModelRegistry, chatModel, responseHandler, responseSchemaValidator);
  }

  @Test
  void rejectsInvalidResponseSchemaBeforeTheTurnStarts() {
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    doThrow(new ConnectorException("INVALID_RESPONSE_SCHEMA", "Schema exceeds configured limits"))
        .when(responseSchemaValidator)
        .validate(any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_RESPONSE_SCHEMA"));
    verifyNoInteractions(agentInstanceClient, chatModelRegistry, chatModel, responseHandler);
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
            c ->
                assertMessagesEqualIgnoringSystemMessageId(
                    c.messages(), SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isEqualTo(assistantMessageText);
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertThat(response.elementActivations()).isEmpty();

    // snapshot is captured before the assistant message is ingested
    assertMessagesEqualIgnoringSystemMessageId(
        chatModelRequestCaptor.getValue().snapshot().messages(), SYSTEM_MESSAGE, USER_MESSAGE);
  }

  @Test
  void orchestratesRequestExecutionWithToolCalls() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);

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
            c ->
                assertMessagesEqualIgnoringSystemMessageId(
                    c.messages(), SYSTEM_MESSAGE, USER_MESSAGE, assistantMessage));

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
                AgentProcessVariables.TOOL_CALL,
                agentResponse.toolCalls().get(0),
                AgentProcessVariables.TOOL_CALL_RESULT,
                ""));
    assertThat(response.elementActivations().get(1).elementId()).isEqualTo("getDateTime");
    assertThat(response.elementActivations().get(1).variables())
        .isEqualTo(
            Map.of(
                AgentProcessVariables.TOOL_CALL,
                agentResponse.toolCalls().get(1),
                AgentProcessVariables.TOOL_CALL_RESULT,
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
            c ->
                assertMessagesEqualIgnoringSystemMessageId(
                    c.messages(), SYSTEM_MESSAGE, interruptedMessage, assistantMessage));

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
        .thenReturn(new CompositionResult.Deferred(List.of()));

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response.variables()).isEmpty();
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.cancelRemainingInstances()).isFalse();
    assertThat(response.elementActivations()).isEmpty();

    verifyNoInteractions(chatModelRegistry, chatModel, responseSchemaValidator);
    // no tool call results arrived at all — nothing to report
    verifyNoInteractions(agentInstanceClient);
  }

  @Test
  void reportsArrivedToolCallResultsFromComposerOnDeferredNoOp() {
    reset(conversationStoreRegistry);
    ConversationStore conversationStore = spy(new InProcessConversationStore());
    doReturn(conversationStore)
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));

    // given: previous turn requested TOOL_CALLS ("abcdef", "fedcba"); the composer reports
    // "abcdef" as already arrived, correlated and gateway-transformed
    final var priorAssistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    final var agentContextWithHistory =
        AgentContext.builder()
            .state(AgentState.READY)
            .toolDefinitions(TOOL_DEFINITIONS)
            .conversation(
                InProcessConversationContext.builder("conv-1")
                    .messages(List.of(USER_MESSAGE, priorAssistantMessage))
                    .build())
            .build();
    final var arrivedResult =
        ToolCallResult.builder().id("abcdef").name("getWeather").content("Sunny").build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(agentContextWithHistory, List.of(arrivedResult)));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.Deferred(List.of(arrivedResult)));

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: still a no-op completion
    assertThat(response.variables()).isEmpty();
    assertThat(response.completionConditionFulfilled()).isFalse();
    verifyNoInteractions(chatModelRegistry, chatModel);

    // and: the arrived result is reported as-is
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<ToolCallResultContent>> resultsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(agentInstanceClient)
        .applyToolCallResults(eq(agentExecutionContext), any(), resultsCaptor.capture(), any());
    assertThat(resultsCaptor.getValue()).containsExactly(ToolCallResultContent.from(arrivedResult));
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
  void shouldRecordTurnStartThenTurnCompletionWithToolCallingStatusWhenToolCalls() {
    // given: LLM returns tool calls → intermediate turn
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: exactly two batched interactions emitted during handleRequest -- start, then completion
    verifyTurnLifecycleRecorded(
        assistantMessage,
        AgentInstanceUpdateStatus.TOOL_CALLING,
        new AgentMetrics(1, new TokenUsage(10, 20), 2));
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldRecordTurnStartThenTurnCompletionWithIdleStatusWhenNoToolCalls() {
    // given: LLM returns no tool calls → final turn → AHSP closes
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = AssistantMessage.builder().build();
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: exactly two batched interactions emitted during handleRequest -- start, then completion
    verifyTurnLifecycleRecorded(
        assistantMessage,
        AgentInstanceUpdateStatus.IDLE,
        new AgentMetrics(1, new TokenUsage(10, 20), 0));
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
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
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new UserPromptConfiguration("user input", List.of()),
                null,
                new LimitsConfiguration(2),
                null,
                null));

    final var contextAtLimit =
        AgentContext.builder()
            .features(AgentFeatures.ofNewInstance())
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

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", null));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED);
              assertThat(e.getErrorVariables()).isEmpty();
            });

    // the provider throws before ingest / history write: the turn-start batch (THINKING status +
    // input messages) is sent before the model call, but the turn-completion write and response
    // handling never happen
    verify(agentInstanceClient, never()).applyTurnCompletion(any(), any(), any(), any(), any());
    verifyNoInteractions(responseHandler);
  }

  @Test
  void throwsWhenModelContextWindowIsExceededBeforeIngestOrHistoryWrite() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContextWindowExceededException("context window exceeded", null));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_CONTEXT_WINDOW_EXCEEDED);
              assertThat(e.getErrorVariables()).isEmpty();
            });

    verify(agentInstanceClient, never()).applyTurnCompletion(any(), any(), any(), any(), any());
    verifyNoInteractions(responseHandler);
  }

  @Test
  void surfacesPartialResultAsRejectionErrorVariablesWhenPresent() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialAssistantMessage =
        AssistantMessage.builder()
            .content(List.of(new TextContent("partial answer before the cutoff", null)))
            .stopReason(StopReason.of("content_filter"))
            .build();
    final var partialResult =
        new ChatModelRejectedException.PartialResult(
            partialAssistantMessage, new AgentMetrics(1, TokenUsage.empty(), 0));

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", partialResult));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorVariables())
                    .isEqualTo(
                        Map.of(
                            "rejection",
                            Map.of(
                                "stopReason", "content_filter",
                                "text", "partial answer before the cutoff"))));
  }

  @Test
  void omitsBlankTextFromRejectionErrorVariables() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialAssistantMessage =
        AssistantMessage.builder().stopReason(StopReason.of("content_filter")).build();
    final var partialResult =
        new ChatModelRejectedException.PartialResult(
            partialAssistantMessage, new AgentMetrics(1, TokenUsage.empty(), 0));

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", partialResult));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorVariables())
                    .isEqualTo(Map.of("rejection", Map.of("stopReason", "content_filter"))));
  }

  @Test
  void throwsWhenGuardrailIntervenesBeforeIngestOrHistoryWrite() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new GuardrailInterventionException("blocked by a guardrail policy", null));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_GUARDRAIL_INTERVENED);
              assertThat(e.getErrorVariables()).isEmpty();
            });

    verify(agentInstanceClient, never()).applyTurnCompletion(any(), any(), any(), any(), any());
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

    // applyTurnStart is only called once, at the top of proceed() — continuation rounds carry no
    // new input and don't restart the turn
    verify(agentInstanceClient)
        .applyTurnStart(eq(agentExecutionContext), any(), any(), any(), any(), any(), any());

    // one applyTurnCompletion call per round: the continuation round re-asserts THINKING with its
    // own metrics, the final round sends the terminal status with its own metrics -- no summing
    final var statusCaptor = ArgumentCaptor.forClass(AgentInstanceUpdateStatus.class);
    verify(agentInstanceClient, times(2))
        .applyTurnCompletion(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any(), statusCaptor.capture());
    assertThat(turnCaptor.getAllValues())
        .extracting(AgentConversationTurn::assistantMessage)
        .containsExactly(partialMessage, doneMessage);
    assertThat(turnCaptor.getAllValues())
        .extracting(AgentConversationTurn::metrics)
        .containsExactly(continuationMetrics, completedMetrics);
    assertThat(statusCaptor.getAllValues())
        .containsExactly(AgentInstanceUpdateStatus.THINKING, AgentInstanceUpdateStatus.IDLE);
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
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
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

  @Test
  void runsABatchOfRuntimeToolCallsInTheJobAndCallsTheModelAgain() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var lookupCall = new ToolCall("call-1", "acme_lookup", Map.of("customerId", "42"));
    final var firstMessage = AssistantMessage.builder().toolCalls(List.of(lookupCall)).build();
    final var secondMessage = assistantMessage("done");
    final var firstMetrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    final var secondMetrics = new AgentMetrics(1, new TokenUsage(10, 20), 0);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Completed(firstMessage, firstMetrics))
        .doReturn(new ChatResult.Completed(secondMessage, secondMetrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());

    // the tool is offered next to the tools of the process, in every request
    for (final var request : chatModelRequestCaptor.getAllValues()) {
      assertThat(request.snapshot().toolDefinitions())
          .extracting(ToolDefinition::name)
          .contains("acme_lookup");
    }

    // the second request carries the runtime tool results of the first round
    final var secondRequestMessages =
        chatModelRequestCaptor.getAllValues().get(1).snapshot().messages();
    assertThat(secondRequestMessages.getLast())
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results())
                    .singleElement()
                    .satisfies(
                        result -> {
                          assertThat(result.id()).isEqualTo("call-1");
                          assertThat(result.name()).isEqualTo("acme_lookup");
                          assertThat(result.elementId()).isNull();
                          assertThat(result.completedAt()).isNotNull();
                          assertThat(result.content())
                              .containsExactly(TextContent.textContent("found the customer"));
                        }));

    // no call goes to the process, the runtime tool is not persisted with the tools
    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertThat(agentResponse.context().toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain("acme_lookup");
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(2, new TokenUsage(20, 40), 1));
    assertThat(agentResponse.context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> {
              assertThat(c.messages()).hasSize(5);
              // the stored message carries the tag of the tool the call targets
              assertThat(c.messages().get(2))
                  .isEqualTo(
                      firstMessage.withToolCalls(List.of(lookupCall.withKind(ToolKind.RUNTIME))));
              assertThat(c.messages().get(3)).isInstanceOf(ToolCallResultMessage.class);
              assertThat(c.messages().get(4)).isEqualTo(secondMessage);
            });

    // history: start of the first turn, completion (THINKING: another round follows), start of the
    // second round with the tool result correlated to the first turn, final completion
    final var inOrder = inOrder(agentInstanceClient);
    inOrder
        .verify(agentInstanceClient)
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), turnCaptor.capture(), any(), any(), any());
    inOrder
        .verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext),
            any(),
            turnCaptor.capture(),
            any(),
            eq(AgentInstanceUpdateStatus.THINKING));
    final var previousTurnCaptor = ArgumentCaptor.forClass(Optional.class);
    inOrder
        .verify(agentInstanceClient)
        .applyTurnStart(
            eq(agentExecutionContext),
            any(),
            any(),
            turnCaptor.capture(),
            previousTurnCaptor.capture(),
            any(),
            any());
    inOrder
        .verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext),
            any(),
            turnCaptor.capture(),
            any(),
            eq(AgentInstanceUpdateStatus.IDLE));
    inOrder.verifyNoMoreInteractions();
    final var turns = turnCaptor.getAllValues();
    assertThat(turns.get(2).iterationKey()).isEqualTo(2);
    assertThat(turns.get(2).inputMessages())
        .singleElement()
        .isInstanceOf(ToolCallResultMessage.class);
    assertThat(previousTurnCaptor.getValue())
        .containsInstanceOf(AgentConversationTurn.class)
        .get()
        .extracting(turn -> ((AgentConversationTurn) turn).assistantMessage())
        .isEqualTo(firstMessage.withToolCalls(List.of(lookupCall.withKind(ToolKind.RUNTIME))));
  }

  @Test
  void givesACallOfARegisteredRuntimeToolThatIsNotEnabledAnErrorResult() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var hiddenCall = new ToolCall("call-1", "acme_hidden", Map.of());
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder().toolCalls(List.of(hiddenCall)).build(), metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());
    // the tool is not offered, and the call never goes to the process
    assertThat(chatModelRequestCaptor.getAllValues().get(0).snapshot().toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain("acme_hidden");
    assertThat(chatModelRequestCaptor.getAllValues().get(1).snapshot().messages().getLast())
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results())
                    .singleElement()
                    .satisfies(
                        result ->
                            assertThat(result.content())
                                .containsExactly(
                                    TextContent.textContent(
                                        "The tool 'acme_hidden' is not enabled."))));
  }

  @Test
  void leavesTheRuntimeCallOfAMixedBatchToTheNextTurnAndHandsTheProcessCallToTheProcess() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var lookupCall = new ToolCall("call-1", "acme_lookup", Map.of("customerId", "42"));
    final var processCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    mockChatModelExecution(
        AssistantMessage.builder().toolCalls(List.of(lookupCall, processCall)).build());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // no extra model call: only the call of the process is handed out
    verify(chatModel, times(1)).execute(any());
    assertThat(chatModelRequestCaptor.getValue().snapshot().toolDefinitions())
        .extracting(ToolDefinition::name)
        .contains("acme_lookup");
    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls())
        .containsExactly(ToolCallProcessVariable.from(processCall));
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.elementActivations())
        .singleElement()
        .satisfies(activation -> assertThat(activation.elementId()).isEqualTo("getWeather"));

    // the runtime tool is neither persisted with the tools nor activated
    assertThat(agentResponse.context().toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain("acme_lookup");

    // the stored assistant message keeps both calls, no result is stored yet
    assertThat(agentResponse.context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> {
              assertThat(c.messages()).hasSize(3);
              assertThat(c.messages().get(2).toString()).contains("acme_lookup");
            });

    // history: one turn start and one completion with the status of a tool calling turn; no
    // result is reported before the turn that follows the results of the process
    final var inOrder = inOrder(agentInstanceClient);
    inOrder
        .verify(agentInstanceClient)
        .applyTurnStart(eq(agentExecutionContext), any(), any(), any(), any(), any(), any());
    inOrder
        .verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext),
            any(),
            turnCaptor.capture(),
            any(),
            eq(AgentInstanceUpdateStatus.TOOL_CALLING));
    inOrder.verifyNoMoreInteractions();
    assertThat(turnCaptor.getValue().toolCalls())
        .containsExactly(lookupCall.withKind(ToolKind.RUNTIME), processCall);
  }

  @Test
  void throwsWhenTheModelCallLimitIsReachedBeforeTheNextRuntimeToolRound() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                new LimitsConfiguration(1),
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-1", "acme_lookup", Map.of())))
                    .build(),
                new AgentMetrics(1, TokenUsage.empty(), 1)))
        .when(chatModel)
        .execute(any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));

    verify(chatModel, times(1)).execute(any());
    // no further round was started
    verify(agentInstanceClient, times(1))
        .applyTurnStart(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void tagsTheToolCallsOfTheAssistantMessageWithTheKindOfTheirToolBeforeTheTurnIsReported() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var lookupCall = new ToolCall("call-1", "acme_lookup", Map.of());
    final var hiddenCall = new ToolCall("call-2", "acme_hidden", Map.of());
    final var processCall = new ToolCall("call-3", "getWeather", Map.of("location", "MUC"));
    mockChatModelExecution(
        AssistantMessage.builder().toolCalls(List.of(lookupCall, hiddenCall, processCall)).build());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // a registered tool is a runtime tool whether it is enabled or not
    final var tagged =
        List.of(
            lookupCall.withKind(ToolKind.RUNTIME),
            hiddenCall.withKind(ToolKind.RUNTIME),
            processCall);
    verify(agentInstanceClient)
        .applyTurnCompletion(eq(agentExecutionContext), any(), turnCaptor.capture(), any(), any());
    assertThat(turnCaptor.getValue().toolCalls()).isEqualTo(tagged);
    assertThat(response.responseValue().context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c ->
                assertThat(((AssistantMessage) c.messages().getLast()).toolCalls())
                    .isEqualTo(tagged));
    assertThat(response.responseValue().toolCalls())
        .containsExactly(ToolCallProcessVariable.from(processCall));
  }

  @Test
  void executesTheRuntimeToolCallsOfThePreviousTurnOnceTheTurnCanBeComposed() {
    final var lookupCall =
        new ToolCall("call-1", "acme_lookup", Map.of()).withKind(ToolKind.RUNTIME);
    final var processCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var context =
        INITIAL_AGENT_CONTEXT.withConversation(
            InProcessConversationContext.builder("conv-1")
                .messages(
                    List.of(
                        USER_MESSAGE,
                        AssistantMessage.builder()
                            .toolCalls(List.of(lookupCall, processCall))
                            .build()))
                .build());
    mockSystemPrompt();
    // the results of the process arrived: the turn needs the results of the runtime tools
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.RuntimeToolResultsRequired(List.of(lookupCall)));
    when(agentInputComposer.composeWithRuntimeToolResults(any(), any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(USER_MESSAGE)));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(context, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    requestHandler.handleRequest(agentExecutionContext);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<RuntimeToolExecution>> executionsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(agentInputComposer)
        .composeWithRuntimeToolResults(any(), any(), any(), any(), executionsCaptor.capture());
    assertThat(executionsCaptor.getValue())
        .extracting(
            execution -> execution.call().id(),
            execution -> execution.result().name(),
            execution -> execution.result().content())
        .containsExactly(
            tuple("call-1", "acme_lookup", List.of(TextContent.textContent("found the customer"))));
  }

  @Test
  void executesTheRuntimeToolCallsOfAMixedBatchInTheJobThatFollowsTheResultsOfTheProcess() {
    final var chatRequests = runMixedBatchInTwoJobs("acme_lookup", () -> {});

    // the first job hands out the call of the process only and runs no tool, the second one runs
    // the tool and returns the results of all calls in the order of the calls
    verify(runtimeToolExecutor, times(1)).execute(any(), any(), anyList());
    assertThat(chatRequests).hasSize(2);
    assertThat(chatRequests.get(1).snapshot().messages().getLast())
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results())
                    .extracting(ToolCallResultContent::id, ToolCallResultContent::content)
                    .containsExactly(
                        tuple("call-1", List.of(TextContent.textContent("found the customer"))),
                        tuple("call-2", List.of(TextContent.textContent("Sunny")))));
  }

  @Test
  void givesTheRuntimeToolCallOfAToolThatIsDisabledBetweenTheTwoJobsAnErrorResult() {
    final var chatRequests = runMixedBatchInTwoJobs("acme_toggle", () -> TOGGLE_ENABLED.set(false));

    // the call was tagged in the first job: it gets an error result and is not left to the process
    // forever
    assertThat(chatRequests).hasSize(2);
    assertThat(chatRequests.get(1).snapshot().messages().getLast())
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results())
                    .extracting(ToolCallResultContent::id, ToolCallResultContent::content)
                    .containsExactly(
                        tuple(
                            "call-1",
                            List.of(
                                TextContent.textContent("The tool 'acme_toggle' is not enabled."))),
                        tuple("call-2", List.of(TextContent.textContent("Sunny")))));
  }

  @Test
  void anInterruptedMixedBatchCancelsOnlyTheCallsOfTheProcessAndStillRunsTheRuntimeTools() {
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                null,
                new EventHandlingConfiguration(EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
                null));
    mockSystemPrompt();
    composeWithTheRealComposer();
    final var lookupCall =
        new ToolCall("call-1", "acme_lookup", Map.of()).withKind(ToolKind.RUNTIME);
    final var processCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var context =
        INITIAL_AGENT_CONTEXT.withConversation(
            InProcessConversationContext.builder("conv-1")
                .messages(
                    List.of(
                        USER_MESSAGE,
                        AssistantMessage.builder()
                            .toolCalls(List.of(lookupCall, processCall))
                            .build()))
                .build());
    // an event arrives while the call of the process is still running
    final var event = ToolCallResult.builder().content("An event occurred").build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(context, List.of(event)));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(runtimeToolExecutor, times(1)).execute(any(), any(), anyList());
    assertThat(chatModelRequestCaptor.getValue().snapshot().messages())
        .filteredOn(ToolCallResultMessage.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message -> {
              assertThat(message.results())
                  .extracting(ToolCallResultContent::id)
                  .containsExactly("call-1", "call-2");
              assertThat(message.results().get(0).content())
                  .containsExactly(TextContent.textContent("found the customer"));
              assertThat(message.results().get(1).content())
                  .containsExactly(TextContent.textContent(ToolCallResult.CONTENT_CANCELLED));
            });
    assertThat(response.cancelRemainingInstances()).isTrue();
  }

  @Test
  void continuesWithARuntimeToolRoundAfterAContinuationRoundInTheSameJob() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var partialMessage = assistantMessage("partial thinking");
    final var lookupMessage =
        AssistantMessage.builder()
            .toolCalls(List.of(new ToolCall("call-1", "acme_lookup", Map.of())))
            .build();
    final var doneMessage = assistantMessage("done");
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 0);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Continuation(partialMessage, metrics))
        .doReturn(new ChatResult.Completed(lookupMessage, metrics))
        .doReturn(new ChatResult.Completed(doneMessage, metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(3)).execute(any());
    verify(runtimeToolExecutor, times(1)).execute(any(), any(), anyList());
    assertThat(chatModelRequestCaptor.getAllValues().get(2).snapshot().messages().getLast())
        .isInstanceOf(ToolCallResultMessage.class);
    assertThat(response.responseValue().responseMessage()).isEqualTo(doneMessage);
    assertThat(response.responseValue().context().metrics().modelCalls()).isEqualTo(3);

    final var statusCaptor = ArgumentCaptor.forClass(AgentInstanceUpdateStatus.class);
    verify(agentInstanceClient, times(3))
        .applyTurnCompletion(
            eq(agentExecutionContext), any(), any(), any(), statusCaptor.capture());
    assertThat(statusCaptor.getAllValues())
        .containsExactly(
            AgentInstanceUpdateStatus.THINKING,
            AgentInstanceUpdateStatus.THINKING,
            AgentInstanceUpdateStatus.IDLE);
  }

  @Test
  void throwsWhenTheModelCallLimitIsReachedAfterARuntimeToolRoundAndRunsNoFurtherTool() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                new LimitsConfiguration(2),
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-1", "acme_lookup", Map.of())))
                    .build(),
                new AgentMetrics(1, TokenUsage.empty(), 1)))
        .doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-2", "acme_lookup", Map.of())))
                    .build(),
                new AgentMetrics(1, TokenUsage.empty(), 1)))
        .when(chatModel)
        .execute(any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));

    verify(chatModel, times(2)).execute(any());
    // the tool of the first round ran; the limit stopped the second round before its tool ran
    verify(runtimeToolExecutor, times(1)).execute(any(), any(), anyList());
  }

  @Test
  void failsWhenAToolOfTheProcessHasTheNameOfARegisteredRuntimeTool() {
    // a tool exposed by a gateway is part of the tool definitions of the agent context
    final var conflicting =
        INITIAL_AGENT_CONTEXT.withToolDefinitions(
            List.of(
                ToolDefinition.builder()
                    .name("acme_hidden")
                    .description("A tool of the process")
                    .inputSchema(Map.of())
                    .build()));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(conflicting, List.of()));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_RUNTIME_TOOL_NAME_CONFLICT);
              assertThat(e.getMessage()).contains("'acme_hidden'");
            });

    verifyNoInteractions(chatModelRegistry, chatModel, agentInputComposer);
  }

  @Test
  void acceptsAToolOfTheProcessWithTheNameOfARuntimeToolForAnAgentWithoutTheFeature() {
    final var legacyContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .toolDefinitions(
                List.of(
                    ToolDefinition.builder()
                        .name("acme_hidden")
                        .description("A tool of the process")
                        .inputSchema(Map.of())
                        .build()))
            .build();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(legacyContext, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(1)).execute(any());
  }

  @Test
  void keepsAnAgentInstanceWithoutTheDocumentsFeatureWithoutRegistry() {
    final var historic = createDocument("historic", "historic.txt");
    final var history =
        List.<Message>of(
            userMessage(List.of(DocumentContent.documentContent(historic))),
            assistantMessage("hello"));
    final var legacyContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .toolDefinitions(TOOL_DEFINITIONS)
            .conversation(InProcessConversationContext.builder("conv").messages(history).build())
            .build();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(legacyContext, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(response.responseValue().context().documents()).isEmpty();
    assertThat(response.responseValue().context().features()).isEmpty();
  }

  @Test
  void keepsAnAgentInstanceWithoutTheRuntimeToolsFeatureWithoutRuntimeTools() {
    final var legacyContext =
        AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(legacyContext, List.of()));
    // a call named like a runtime tool belongs to the process of such an instance
    final var lookupCall = new ToolCall("call-1", "acme_lookup", Map.of());
    mockChatModelExecution(AssistantMessage.builder().toolCalls(List.of(lookupCall)).build());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(1)).execute(any());
    assertThat(chatModelRequestCaptor.getValue().snapshot().toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain("acme_lookup");
    assertThat(response.responseValue().toolCalls())
        .containsExactly(ToolCallProcessVariable.from(lookupCall));
    assertThat(response.responseValue().context().features()).isEmpty();
  }

  @Test
  void persistsAndReportsTheDocumentsFromTheComposedTurn() {
    final var document = createDocument("tool data", "tool.txt");
    mockSystemPrompt();
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(
            new CompositionResult.NextTurn(
                List.of(USER_MESSAGE),
                List.of(
                    new IngestedDocument(document, DocumentSource.toolCall("search", "call-1")))));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_DOCUMENTS, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    final var context = response.responseValue().context();
    assertThat(context.documents())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.id()).isEqualTo(DocumentHandle.idFor(document));
              assertThat(entry.source().type()).isEqualTo(DocumentSource.Type.TOOL_CALL);
              assertThat(entry.source().toolName()).isEqualTo("search");
              assertThat(entry.source().toolCallId()).isEqualTo("call-1");
              assertThat(entry.reference()).containsEntry("camunda.document.type", "camunda");
            });
    assertThat(response.variables().get("agentContext")).isEqualTo(context);

    // the agent instance history is written with the registry of the turn
    final var startContext = ArgumentCaptor.forClass(AgentContext.class);
    verify(agentInstanceClient)
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), any(), any(), any(), startContext.capture());
    assertThat(startContext.getValue().documentRegistry().findById(DocumentHandle.idFor(document)))
        .isPresent();
  }

  @Test
  void registersTheDocumentsOfARuntimeToolRoundBeforeTheModelIsCalledAgain() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_DOCUMENTS, List.of()));
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-1", "acme_create_report", Map.of())))
                    .build(),
                metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // the document is registered with the call that returned it
    assertThat(response.responseValue().context().documents())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.id()).isEqualTo(DocumentHandle.idFor(NEW_REPORT));
              assertThat(entry.source())
                  .isEqualTo(DocumentSource.toolCall("acme_create_report", "call-1"));
            });

    // the history of the second round is written with the registry that holds the document
    final var startContexts = ArgumentCaptor.forClass(AgentContext.class);
    verify(agentInstanceClient, times(2))
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), any(), any(), any(), startContexts.capture());
    assertThat(startContexts.getAllValues().get(0).documents()).isEmpty();
    assertThat(startContexts.getAllValues().get(1).documents()).hasSize(1);
  }

  @Test
  void theCallsOfOneMessageSeeTheDocumentsKnownAtTheStartAndTheNextRoundSeesTheReturnedOnes() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_DOCUMENTS, List.of()));
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 2);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            new ToolCall("call-1", "acme_create_report", Map.of()),
                            new ToolCall("call-2", "acme_count_documents", Map.of())))
                    .build(),
                metrics))
        .doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-3", "acme_count_documents", Map.of())))
                    .build(),
                metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    requestHandler.handleRequest(agentExecutionContext);

    final var requests = chatModelRequestCaptor.getAllValues();
    // the call that counts does not see the document the call before it returned
    assertThat(
            ((ToolCallResultMessage) requests.get(1).snapshot().messages().getLast())
                .results()
                .get(1)
                .content())
        .containsExactly(TextContent.textContent("documents: 0"));
    // the next round knows it
    assertThat(
            ((ToolCallResultMessage) requests.get(2).snapshot().messages().getLast())
                .results()
                .getFirst()
                .content())
        .containsExactly(TextContent.textContent("documents: 1"));
  }

  @Test
  void anErrorResultOfARuntimeToolRegistersNoDocument() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_DOCUMENTS, List.of()));
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(new ToolCall("call-1", "acme_create_report", Map.of("fail", true))))
                    .build(),
                metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(response.responseValue().context().documents()).isEmpty();
    assertThat(chatModelRequestCaptor.getAllValues().get(1).snapshot().messages().getLast())
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results().getFirst().content())
                    .containsExactly(TextContent.textContent("The report could not be created.")));
  }

  @Test
  void aJobThatRunsAgainFromTheSameContextRegistersTheSameDocuments() {
    final var runs = new ArrayList<AgentContext>();
    for (int attempt = 0; attempt < 2; attempt++) {
      reset(chatModelRegistry, chatModel, systemPromptComposer, responseHandler, agentInitializer);
      mockSystemPrompt();
      mockProceed(USER_MESSAGE);
      when(agentInitializer.initializeAgent(agentExecutionContext))
          .thenReturn(new ReadyToConverse(CONTEXT_WITH_DOCUMENTS, List.of()));
      final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
      doReturn(chatModel).when(chatModelRegistry).resolve(any());
      doReturn(
              new ChatResult.Completed(
                  AssistantMessage.builder()
                      .toolCalls(List.of(new ToolCall("call-1", "acme_create_report", Map.of())))
                      .build(),
                  metrics))
          .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
          .when(chatModel)
          .execute(any());
      mockResponseHandler();

      runs.add(requestHandler.handleRequest(agentExecutionContext).responseValue().context());
    }

    assertThat(runs.get(0).documents()).hasSize(1);
    assertThat(runs.get(1).documents()).isEqualTo(runs.get(0).documents());
  }

  @Test
  void registersTheDocumentsOfARuntimeToolAndOfTheProcessInCallOrderInAMixedBatch() {
    final var processDocument = createDocument("process data", "process.txt");

    final var run =
        runMixedBatchInTwoJobs(
            "acme_create_report", CONTEXT_WITH_DOCUMENTS, processDocument, () -> {});

    assertThat(run.finalContext().documents())
        .extracting(entry -> entry.id(), entry -> entry.source())
        .containsExactly(
            tuple(
                DocumentHandle.idFor(NEW_REPORT),
                DocumentSource.toolCall("acme_create_report", "call-1")),
            tuple(
                DocumentHandle.idFor(processDocument),
                DocumentSource.toolCall("getWeather", "call-2")));
  }

  /**
   * Composes the turn input with the real composer, for the jobs that follow the results of the
   * process.
   */
  private void composeWithTheRealComposer() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    lenient()
        .when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(i -> i.getArgument(1));
    final var realComposer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
    lenient()
        .when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenAnswer(
            i ->
                realComposer.compose(
                    i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3)));
    lenient()
        .when(agentInputComposer.composeWithRuntimeToolResults(any(), any(), any(), any(), any()))
        .thenAnswer(
            i ->
                realComposer.composeWithRuntimeToolResults(
                    i.getArgument(0),
                    i.getArgument(1),
                    i.getArgument(2),
                    i.getArgument(3),
                    i.getArgument(4)));
  }

  /**
   * Runs a message that calls the runtime tool {@code runtimeToolName} and a tool of the process in
   * one job, then the job that follows the result of the process.
   *
   * @param betweenJobs what happens after the first job
   * @return the requests sent to the chat model, one per job
   */
  private List<ChatRequest> runMixedBatchInTwoJobs(String runtimeToolName, Runnable betweenJobs) {
    return runMixedBatchInTwoJobs(runtimeToolName, INITIAL_AGENT_CONTEXT, "Sunny", betweenJobs)
        .chatRequests();
  }

  /** What the two jobs of a mixed batch produced. */
  private record MixedBatchRun(List<ChatRequest> chatRequests, AgentContext finalContext) {}

  private MixedBatchRun runMixedBatchInTwoJobs(
      String runtimeToolName,
      AgentContext initialContext,
      Object processResultContent,
      Runnable betweenJobs) {
    mockSystemPrompt();
    composeWithTheRealComposer();
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 2);
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            new ToolCall("call-1", runtimeToolName, Map.of()),
                            new ToolCall("call-2", "getWeather", Map.of("location", "MUC"))))
                    .build(),
                metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("done"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(initialContext, List.of()));
    final var firstJob = requestHandler.handleRequest(agentExecutionContext);
    assertThat(firstJob.responseValue().toolCalls())
        .extracting(call -> call.metadata().id())
        .containsExactly("call-2");
    verify(runtimeToolExecutor, never()).execute(any(), any(), anyList());

    betweenJobs.run();

    final var arrivedResult =
        ToolCallResult.builder()
            .id("call-2")
            .name("getWeather")
            .content(processResultContent)
            .build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new ReadyToConverse(firstJob.responseValue().context(), List.of(arrivedResult)));
    final var secondJob = requestHandler.handleRequest(agentExecutionContext);

    return new MixedBatchRun(
        chatModelRequestCaptor.getAllValues(), secondJob.responseValue().context());
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
              final var lastTurn = conversation.lastTurn().orElseThrow();
              final var assistantMessage = lastTurn.assistantMessage();
              final var toolCalls =
                  lastTurn.processToolCalls().stream().map(ToolCallProcessVariable::from).toList();
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

  /** Verifies the two batched agent-instance interactions a chat turn produces, in order. */
  private void verifyTurnLifecycleRecorded(
      AssistantMessage expectedAssistantMessage,
      AgentInstanceUpdateStatus expectedFinalStatus,
      AgentMetrics expectedMetrics) {
    verify(agentInstanceClient)
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), turnCaptor.capture(), any(), any(), any());
    assertThat(turnCaptor.getValue().inputMessages()).containsExactly(USER_MESSAGE);

    verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any(), eq(expectedFinalStatus));
    assertThat(turnCaptor.getValue().assistantMessage()).isEqualTo(expectedAssistantMessage);
    assertThat(turnCaptor.getValue().metrics().withExecutionTime(null)).isEqualTo(expectedMetrics);
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
