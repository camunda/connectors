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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
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
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.DocumentTag;
import io.camunda.connector.agenticai.aiagent.document.GetDocumentTool;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.document.ReturnDocumentTool;
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
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.agenticai.aiagent.model.ReturnedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
          .features(AgentContext.newInstanceFeatures())
          .state(AgentState.READY)
          .toolDefinitions(TOOL_DEFINITIONS)
          .build();

  private static final AgentContext CONTEXT_WITH_UPLOAD_TOOL =
      AgentContext.builder()
          .features(AgentContext.newInstanceFeatures())
          .state(AgentState.READY)
          .toolDefinitions(
              Stream.concat(
                      TOOL_DEFINITIONS.stream(),
                      Stream.of(
                          ToolDefinition.builder()
                              .name("upload")
                              .inputSchema(
                                  Map.of(
                                      "type",
                                      "object",
                                      "properties",
                                      Map.of(
                                          "doc",
                                          Map.of(
                                              "type", "string", "format", "camunda-document-id"))))
                              .build()))
                  .toList())
          .build();

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

  @Spy
  private DocumentExpander documentExpander =
      new DocumentExpander(TestObjectMapperSupplier.INSTANCE);

  @Spy
  private RuntimeToolCallHandler runtimeToolCallHandler =
      new RuntimeToolCallHandlerImpl(
          new RuntimeToolRegistryImpl(
              List.of(
                  new ReturnDocumentTool(TestObjectMapperSupplier.INSTANCE),
                  new GetDocumentTool(TestObjectMapperSupplier.INSTANCE))));

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AgentSubProcessExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<ChatRequest> chatModelRequestCaptor;
  @Captor private ArgumentCaptor<AgentConversationTurn> turnCaptor;

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
            .features(AgentContext.newInstanceFeatures())
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

    verifyNoInteractions(agentInputComposer, chatModelRegistry, chatModel, responseHandler);
  }

  @Test
  void dispatchesToolDiscoveryStatusUpdateDuringHandleRequest() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder()
            .features(AgentContext.newInstanceFeatures())
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

    verifyNoInteractions(chatModelRegistry, chatModel);
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
    final var knownDocument = createDocument("known", "known.txt");
    final var knownEntry =
        DocumentRegistryEntry.from(
            new IngestedDocument(knownDocument, DocumentSource.toolCall("search", "call-0", null)));
    final var priorAssistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    final var agentContextWithHistory =
        AgentContext.builder()
            .features(AgentContext.newInstanceFeatures())
            .state(AgentState.READY)
            .toolDefinitions(TOOL_DEFINITIONS)
            .documents(List.of(knownEntry))
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
    final var contextCaptor = ArgumentCaptor.forClass(AgentContext.class);
    verify(agentInstanceClient)
        .applyToolCallResults(
            eq(agentExecutionContext),
            any(),
            resultsCaptor.capture(),
            any(),
            contextCaptor.capture());
    assertThat(resultsCaptor.getValue()).containsExactly(ToolCallResultContent.from(arrivedResult));
    assertThat(contextCaptor.getValue().documentRegistry().entries()).containsExactly(knownEntry);
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
            .features(AgentContext.newInstanceFeatures())
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
    verify(agentInstanceClient, never())
        .applyTurnCompletion(any(), any(), any(), any(), any(), any());
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

    verify(agentInstanceClient, never())
        .applyTurnCompletion(any(), any(), any(), any(), any(), any());
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

    verify(agentInstanceClient, never())
        .applyTurnCompletion(any(), any(), any(), any(), any(), any());
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
            eq(agentExecutionContext),
            any(),
            turnCaptor.capture(),
            any(),
            statusCaptor.capture(),
            any());
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
  void answersAToolCallWithAnUnknownDocumentIdLocallyAndCallsTheModelAgainInTheSameJob() {
    final var document = createDocument("report data", "report.txt");
    final var documentId = DocumentHandle.idFor(document);
    mockSystemPrompt();
    mockProceedWithDocument(document);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_UPLOAD_TOOL, List.of()));

    final var badCall = new ToolCall("call-1", "upload", Map.of("doc", "bogus"));
    final var goodCall = new ToolCall("call-2", "upload", Map.of("doc", documentId));
    final var firstMessage = AssistantMessage.builder().toolCalls(List.of(badCall)).build();
    final var secondMessage = AssistantMessage.builder().toolCalls(List.of(goodCall)).build();
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Completed(firstMessage, metrics))
        .doReturn(new ChatResult.Completed(secondMessage, metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());

    // the second request carries the error result of the first round
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
                          assertThat(result.name()).isEqualTo("upload");
                          assertThat(result.completedAt()).isNotNull();
                          assertThat(result.content())
                              .singleElement()
                              .isInstanceOfSatisfying(
                                  TextContent.class,
                                  text ->
                                      assertThat(text.text())
                                          .isEqualTo(
                                              "Parameter 'doc' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'bogus'."));
                        }));

    // the tool call of the final round goes to the process; the first round stays in the history
    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls()).containsExactly(ToolCallProcessVariable.from(goodCall));
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(2, new TokenUsage(20, 40), 2));
    assertThat(agentResponse.context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> {
              assertThat(c.messages()).hasSize(5);
              assertThat(c.messages().get(2)).isEqualTo(firstMessage);
              assertThat(c.messages().get(3)).isInstanceOf(ToolCallResultMessage.class);
              assertThat(c.messages().get(4)).isEqualTo(secondMessage);
            });

    // history: start of the first turn, completion (THINKING: another round follows), start of the
    // synthetic round with the tool result correlated to the first turn, final completion
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
            eq(AgentInstanceUpdateStatus.THINKING),
            any());
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
            eq(AgentInstanceUpdateStatus.TOOL_CALLING),
            any());
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
        .isEqualTo(firstMessage);
  }

  @Test
  void carriesTheContentOfADocumentRequestedByAGetDocumentCallIntoTheNextRoundOfTheSameJob() {
    final var document = createDocument("report data", "report.txt");
    final var documentId = DocumentHandle.idFor(document);
    mockSystemPrompt();
    mockProceedWithDocument(document);
    final var realComposer =
        new AgentConversationTurnInputComposerImpl(mock(GatewayToolHandlerRegistry.class));
    when(agentInputComposer.composeDocumentContentMessage(any(), any()))
        .thenAnswer(
            i -> realComposer.composeDocumentContentMessage(i.getArgument(0), i.getArgument(1)));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var getCall = new ToolCall("call-1", GetDocumentTool.NAME, Map.of("id", documentId));
    final var firstMessage = AssistantMessage.builder().toolCalls(List.of(getCall)).build();
    final var secondMessage = assistantMessage("done");
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(new ChatResult.Completed(firstMessage, metrics))
        .doReturn(new ChatResult.Completed(secondMessage, metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());

    // the first request shows the document as a tag only
    final var firstRequestMessages =
        chatModelRequestCaptor.getAllValues().get(0).snapshot().messages();
    assertThat(firstRequestMessages)
        .noneMatch(
            message ->
                message instanceof UserMessage user
                    && user.content().stream().anyMatch(DocumentContent.class::isInstance));

    // the second request has the tool result followed by the message with the loaded content
    final var secondRequestMessages =
        chatModelRequestCaptor.getAllValues().get(1).snapshot().messages();
    assertThat(secondRequestMessages.get(secondRequestMessages.size() - 2))
        .isInstanceOfSatisfying(
            ToolCallResultMessage.class,
            message ->
                assertThat(message.results())
                    .singleElement()
                    .satisfies(
                        result -> {
                          assertThat(result.id()).isEqualTo("call-1");
                          assertThat(result.content()).hasSize(3);
                          assertThat(result.content().get(0))
                              .isEqualTo(TextContent.textContent("Loaded the document."));
                          assertThat(result.content().get(1))
                              .isInstanceOfSatisfying(
                                  DocumentContent.class,
                                  loaded ->
                                      assertThat(DocumentHandle.idFor(loaded.document()))
                                          .isEqualTo(documentId));
                          assertThat(result.content().get(2))
                              .isEqualTo(
                                  TextContent.textContent(
                                      "Its content follows in the next message."));
                        }));
    assertThat(secondRequestMessages.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(message.content()).hasSize(3);
              assertThat(message.content().get(0))
                  .isEqualTo(
                      TextContent.textContent(
                          AgentConversationTurnInputComposerImpl.DOCUMENT_CONTENT_PREAMBLE));
              assertThat(message.content().get(1))
                  .isInstanceOfSatisfying(
                      TextContent.class,
                      tag ->
                          assertThat(DocumentTag.idOfSingleTag(tag.text())).isEqualTo(documentId));
              assertThat(message.content().get(2))
                  .isInstanceOfSatisfying(
                      DocumentContent.class,
                      content ->
                          assertThat(DocumentHandle.idFor(content.document()))
                              .isEqualTo(documentId));
            });

    // the stored conversation keeps the follow-up message as the document reference with the
    // delivery content only
    assertThat(response.responseValue().context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> {
              assertThat(c.messages()).hasSize(6);
              assertThat(c.messages().get(3)).isInstanceOf(ToolCallResultMessage.class);
              assertThat(c.messages().get(4))
                  .isInstanceOfSatisfying(
                      UserMessage.class,
                      message -> {
                        assertThat(message.content())
                            .noneMatch(DocumentContent.class::isInstance)
                            .hasSize(2);
                        assertThat(message.content().get(1))
                            .isInstanceOfSatisfying(
                                DocumentReferenceContent.class,
                                reference -> {
                                  assertThat(reference.id()).isEqualTo(documentId);
                                  assertThat(reference.delivery())
                                      .isEqualTo(DocumentDelivery.CONTENT);
                                });
                      });
            });

    // the synthetic round reports both messages as input of its turn
    verify(agentInstanceClient, times(2))
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), turnCaptor.capture(), any(), any(), any());
    assertThat(turnCaptor.getAllValues().get(1).inputMessages())
        .hasSize(2)
        .satisfiesExactly(
            first -> assertThat(first).isInstanceOf(ToolCallResultMessage.class),
            second -> assertThat(second).isInstanceOf(UserMessage.class));
  }

  @Test
  void runsTheValidCallsOfABatchWithAnUnknownDocumentIdAndKeepsTheErrorResultPending() {
    final var document = createDocument("report data", "report.txt");
    mockSystemPrompt();
    mockProceedWithDocument(document);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_UPLOAD_TOOL, List.of()));

    final var badCall = new ToolCall("call-1", "upload", Map.of("doc", "bogus"));
    final var otherCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var message = AssistantMessage.builder().toolCalls(List.of(badCall, otherCall)).build();
    mockChatModelExecution(message);
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // no extra model call: the valid call is handed to the process
    verify(chatModel, times(1)).execute(any());
    final var agentResponse = response.responseValue();
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls()).containsExactly(ToolCallProcessVariable.from(otherCall));
    assertThat(response.completionConditionFulfilled()).isFalse();
    assertThat(response.elementActivations())
        .singleElement()
        .satisfies(activation -> assertThat(activation.elementId()).isEqualTo("getWeather"));

    // the error result waits in the agent context for the results of the process
    assertThat(agentResponse.context().pendingRuntimeToolCallResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("call-1");
              assertThat(result.name()).isEqualTo("upload");
              assertThat(result.completedAt()).isNotNull();
              assertThat(result.content())
                  .containsExactly(
                      TextContent.textContent(
                          "Parameter 'doc' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'bogus'."));
            });

    // the stored assistant message keeps both calls, no results are stored yet
    assertThat(agentResponse.context().conversation())
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> {
              assertThat(c.messages()).hasSize(3);
              assertThat(c.messages().get(2)).isEqualTo(message);
            });

    // history: one turn start, one completion with the status of a tool calling turn, then the
    // runtime result right away (status-less), correlated to the completed assistant turn
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
            eq(AgentInstanceUpdateStatus.TOOL_CALLING),
            any());
    final var reportedResultsCaptor = ArgumentCaptor.forClass(List.class);
    inOrder
        .verify(agentInstanceClient)
        .applyToolCallResults(
            eq(agentExecutionContext),
            any(),
            reportedResultsCaptor.capture(),
            same(turnCaptor.getValue()),
            any());
    inOrder.verifyNoMoreInteractions();
    assertThat(turnCaptor.getValue().toolCalls()).containsExactly(badCall, otherCall);
    // the reported result is the one kept pending, with the same completion timestamp
    assertThat(reportedResultsCaptor.getValue())
        .isEqualTo(agentResponse.context().pendingRuntimeToolCallResults());
  }

  @Test
  void runsTheRealCallOfABatchWithAReturnDocumentCallAndRecordsTheResponseDocumentImmediately() {
    final var document = createDocument("report data", "report.txt");
    final var documentId = DocumentHandle.idFor(document);
    mockSystemPrompt();
    mockProceedWithDocument(document);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                null,
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var returnCall =
        new ToolCall(
            "call-1", ReturnDocumentTool.NAME, Map.of("key", "report", "document", documentId));
    final var otherCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    mockChatModelExecution(
        AssistantMessage.builder().toolCalls(List.of(returnCall, otherCall)).build());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(1)).execute(any());
    // the built-in tool is offered, but is neither persisted nor activated
    assertThat(chatModelRequestCaptor.getValue().snapshot().toolDefinitions())
        .extracting(ToolDefinition::name)
        .contains(ReturnDocumentTool.NAME);
    final var context = response.responseValue().context();
    assertThat(context.toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain(ReturnDocumentTool.NAME);
    assertThat(response.responseValue().toolCalls())
        .containsExactly(ToolCallProcessVariable.from(otherCall));
    assertThat(response.elementActivations()).hasSize(1);

    // the response documents are persisted with this job, the answer waits for the process result
    assertThat(context.responseDocuments())
        .containsExactly(Map.entry("report", new ResponseDocumentSelection(documentId, null)));
    assertThat(context.pendingRuntimeToolCallResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("call-1");
              assertThat(result.content())
                  .containsExactly(
                      TextContent.textContent("Returned the document under the key 'report'."));
            });
  }

  @Test
  void
      keepsAllResultsOutOfTheContextOfABatchAnsweredEntirelyByRuntimeToolsBecauseTheModelIsCalledAgain() {
    final var document = createDocument("report data", "report.txt");
    final var documentId = DocumentHandle.idFor(document);
    mockSystemPrompt();
    mockProceedWithDocument(document);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                null,
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var returnCall =
        new ToolCall(
            "call-1", ReturnDocumentTool.NAME, Map.of("key", "report", "document", documentId));
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder().toolCalls(List.of(returnCall)).build(), metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("here you go"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    verify(chatModel, times(2)).execute(any());
    final var results =
        chatModelRequestCaptor.getAllValues().get(1).snapshot().messages().stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .findFirst()
            .orElseThrow()
            .results();
    assertThat(results)
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("call-1");
              assertThat(result.content())
                  .singleElement()
                  .isInstanceOfSatisfying(
                      TextContent.class,
                      text ->
                          assertThat(text.text())
                              .isEqualTo("Returned the document under the key 'report'."));
            });

    final var context = response.responseValue().context();
    assertThat(context.responseDocuments())
        .containsExactly(Map.entry("report", new ResponseDocumentSelection(documentId, null)));
    assertThat(context.pendingRuntimeToolCallResults()).isEmpty();
    assertThat(response.responseValue().toolCalls()).isEmpty();
    assertThat(response.elementActivations()).isEmpty();
  }

  @Test
  void clearsThePendingRuntimeResultsOnceTheComposerConsumedThem() {
    final var pending =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("upload")
            .content(List.of(TextContent.textContent("rejected")))
            .completedAt(OffsetDateTime.now())
            .build();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new ReadyToConverse(
                INITIAL_AGENT_CONTEXT.withPendingRuntimeToolCallResults(List.of(pending)),
                List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(response.responseValue().context().pendingRuntimeToolCallResults()).isEmpty();
  }

  @Test
  void keepsThePendingRuntimeResultsWhileTheProcessResultsAreStillMissing() {
    final var pending =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("upload")
            .content(List.of(TextContent.textContent("rejected")))
            .completedAt(OffsetDateTime.now())
            .build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new ReadyToConverse(
                INITIAL_AGENT_CONTEXT.withPendingRuntimeToolCallResults(List.of(pending)),
                List.of()));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.Deferred(List.of()));

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // a no-op completion writes no agent context, so the pending results stay where they are
    assertThat(response.responseValue()).isNull();
    verifyNoInteractions(chatModelRegistry, chatModel, responseHandler);
  }

  @Test
  void answersAReturnDocumentCallWithAnUnknownIdWithAnErrorAndRecordsNothing() {
    final var document = createDocument("report data", "report.txt");
    mockSystemPrompt();
    mockProceedWithDocument(document);
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                null,
                null,
                null));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var returnCall =
        new ToolCall(
            "call-1", ReturnDocumentTool.NAME, Map.of("key", "report", "document", "bogus"));
    final var metrics = new AgentMetrics(1, new TokenUsage(10, 20), 1);
    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder().toolCalls(List.of(returnCall)).build(), metrics))
        .doReturn(new ChatResult.Completed(assistantMessage("sorry"), metrics))
        .when(chatModel)
        .execute(chatModelRequestCaptor.capture());
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    final var results =
        ((ToolCallResultMessage)
                chatModelRequestCaptor.getAllValues().get(1).snapshot().messages().getLast())
            .results();
    assertThat(results)
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .singleElement()
                    .isInstanceOfSatisfying(
                        TextContent.class,
                        text ->
                            assertThat(text.text())
                                .startsWith(
                                    "Parameter 'document' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'bogus'.")));
    assertThat(response.responseValue().context().responseDocuments()).isEmpty();
  }

  @Test
  void carriesTheResponseDocumentsIntoTheAgentResponseVariable() {
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    mockChatModelExecution(assistantMessage("here you go"));
    final var returned =
        new ReturnedDocument(Map.<String, Object>of("camunda.document.type", "camunda"), "Report");
    when(responseHandler.createResponse(any(AgentConversation.class)))
        .thenAnswer(
            i ->
                AgentResponse.builder()
                    .context(i.getArgument(0, AgentConversation.class).toAgentContext())
                    .responseText("here you go")
                    .responseDocuments(new LinkedHashMap<>(Map.of("report", returned)))
                    .build());

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(response.variables().get("agent"))
        .isInstanceOfSatisfying(
            AgentSubProcessResponse.class,
            agent ->
                assertThat(agent.responseDocuments())
                    .containsExactly(Map.entry("report", returned)));
  }

  @Test
  void throwsWhenTheModelCallLimitIsReachedBeforeTheRetryRound() {
    final var document = createDocument("report data", "report.txt");
    mockSystemPrompt();
    mockProceedWithDocument(document);
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
        .thenReturn(new ReadyToConverse(CONTEXT_WITH_UPLOAD_TOOL, List.of()));

    doReturn(chatModel).when(chatModelRegistry).resolve(any());
    doReturn(
            new ChatResult.Completed(
                AssistantMessage.builder()
                    .toolCalls(List.of(new ToolCall("call-1", "upload", Map.of("doc", "bogus"))))
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
    // no synthetic round was started
    verify(agentInstanceClient, times(1))
        .applyTurnStart(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void persistsDocumentsFromTheComposedTurnInTheReturnedAgentContext() {
    final var document = createDocument("tool data", "tool.txt");
    mockSystemPrompt();
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(
            new CompositionResult.NextTurn(
                List.of(USER_MESSAGE),
                List.of(
                    new IngestedDocument(
                        document, DocumentSource.toolCall("search", "call-1", null)))));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
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
  }

  @Test
  void reportsTheToolsAndTheDocumentsOfTheTurnToTheAgentInstanceHistory() {
    final var document = createDocument("tool data", "tool.txt");
    mockSystemPrompt();
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(
            new CompositionResult.NextTurn(
                List.of(USER_MESSAGE),
                List.of(
                    new IngestedDocument(
                        document, DocumentSource.toolCall("search", "call-1", null)))));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    requestHandler.handleRequest(agentExecutionContext);

    final var startContext = ArgumentCaptor.forClass(AgentContext.class);
    verify(agentInstanceClient)
        .applyTurnStart(
            eq(agentExecutionContext), any(), any(), any(), any(), any(), startContext.capture());
    final var completionContext = ArgumentCaptor.forClass(AgentContext.class);
    verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext), any(), any(), any(), any(), completionContext.capture());
    for (final var context : List.of(startContext.getValue(), completionContext.getValue())) {
      assertThat(context.documentRegistry().findById(DocumentHandle.idFor(document))).isPresent();
    }
  }

  @Test
  void keepsAnAgentInstanceWithoutTheDocumentsFeatureWithoutRegistryAndFeatures() {
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
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(USER_MESSAGE)));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(legacyContext, List.of()));
    mockChatModelExecution(assistantMessage("done"));
    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(response.responseValue().context().documents()).isEmpty();
    assertThat(response.responseValue().context().features()).isEmpty();
    assertThat(response.responseValue().context().responseDocuments()).isEmpty();
  }

  private Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }

  private void mockSystemPrompt() {
    when(systemPromptComposer.compose(any(), any())).thenReturn(SYSTEM_PROMPT);
  }

  private void mockProceedWithDocument(Document document) {
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(
            new CompositionResult.NextTurn(
                List.of(USER_MESSAGE),
                List.of(
                    new IngestedDocument(
                        document, DocumentSource.userMessage(USER_MESSAGE.id().toString())))));
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
              final var runtimeAnsweredIds =
                  conversation.pendingRuntimeToolCallResults().stream()
                      .map(ToolCallResultContent::id)
                      .collect(Collectors.toSet());
              final var toolCalls =
                  assistantMessage.toolCalls() == null
                      ? List.<ToolCallProcessVariable>of()
                      : assistantMessage.toolCalls().stream()
                          .filter(toolCall -> !runtimeAnsweredIds.contains(toolCall.id()))
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
            eq(agentExecutionContext),
            any(),
            turnCaptor.capture(),
            any(),
            eq(expectedFinalStatus),
            any());
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
