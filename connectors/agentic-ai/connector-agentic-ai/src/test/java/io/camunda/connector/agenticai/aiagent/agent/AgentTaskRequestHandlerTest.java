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
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.testutil.MessageAssertions.assertMessagesEqualIgnoringSystemMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskRequestHandlerTest {

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
  @Mock private ChatModelRegistry chatModelRegistry;
  @Mock private ChatModel chatModel;
  @Mock private SystemPromptComposer systemPromptComposer;
  @Mock private AgentResponseHandler responseHandler;
  @Mock private AgentInstanceClient agentInstanceClient;
  @Mock private AgentTaskExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<ChatRequest> chatModelRequestCaptor;

  @InjectMocks private AgentTaskRequestHandler requestHandler;

  @BeforeEach
  void setUp() {
    doReturn(new InProcessConversationStore())
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
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

    verifyNoInteractions(agentInputComposer, chatModelRegistry, chatModel, responseHandler);
  }

  @Test
  void dispatchesToolDiscoveryStatusUpdateDuringHandleRequest() {
    reset(conversationStoreRegistry);

    final var toolDiscoveryToolCalls =
        List.of(ToolCall.builder().id("tool_discovery").name("AGatewayTool").build());
    final var discoveryAgentContext =
        AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build();

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
    assertThat(response.agentResponse()).isNull();

    verifyNoInteractions(agentInputComposer, chatModelRegistry, chatModel, responseHandler);
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
    mockChatModelExecution(assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    // snapshot is captured before the assistant message is ingested
    assertMessagesEqualIgnoringSystemMessageId(
        chatModelRequestCaptor.getValue().snapshot().messages(), SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
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
  }

  @Test
  void orchestratesRequestExecutionWithToolCalls() {
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();
    mockChatModelExecution(assistantMessage);

    mockResponseHandler();

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertMessagesEqualIgnoringSystemMessageId(
        chatModelRequestCaptor.getValue().snapshot().messages(), SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
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

    verifyNoInteractions(chatModelRegistry, chatModel, agentInstanceClient);
  }

  @Test
  void reportsIdleToAgentInstanceWhenJobFailsWithARecoverableAgentInstanceKey() {
    mockConfiguration();
    final var agentInstanceKey = AgentInstanceKey.of(42L);
    when(agentExecutionContext.initialAgentContext())
        .thenReturn(
            AgentContext.builder()
                .state(AgentState.READY)
                .toolDefinitions(TOOL_DEFINITIONS)
                .metadata(new AgentMetadata(1L, 1L, 42L, null))
                .build());

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NoInput());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("NO_USER_MESSAGE_CONTENT"));

    verify(agentInstanceClient).reportIdleOnFailure(agentExecutionContext, agentInstanceKey);
  }

  @Test
  void reportsIdleWhenModelCallFailsOnANewlyCreatedAgentInstanceWithNoInitialAgentContext() {
    // brand-new agent, first job: initialAgentContext() reflects the pre-invocation state and has
    // no metadata yet (deliberately left un-stubbed, defaulting to null) -- the recoverable key
    // must come from the in-flight AgentContext returned by initializeAgent() instead
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    final var freshAgentContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .toolDefinitions(TOOL_DEFINITIONS)
            .metadata(new AgentMetadata(1L, 1L, 99L, null))
            .build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(freshAgentContext, List.of()));

    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", null));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED));

    verify(agentInstanceClient)
        .reportIdleOnFailure(agentExecutionContext, AgentInstanceKey.of(99L));
  }

  @Test
  void doesNotReportIdleWhenFailureOriginatesFromAnAgentInstanceUpdate() {
    // no initialAgentContext() stub: the recursive-failure guard must short-circuit before ever
    // resolving a key, so this key-resolution path is never even reached for this scenario
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    doThrow(
            new ConnectorException(
                AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED, "update failed"))
        .when(agentInstanceClient)
        .applyTurnStart(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED));

    verify(agentInstanceClient, never()).reportIdleOnFailure(any(), any());
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
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
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
    verifyNoInteractions(chatModelRegistry, chatModel);
  }

  @Test
  void shouldRecordTurnStartThenTurnCompletionWithIdleStatusWhenNoToolCalls() {
    // given
    mockConfiguration();
    mockSystemPrompt();
    mockProceed(USER_MESSAGE);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
    final var assistantMessage = assistantMessage("No tool calls here.");
    mockChatModelExecution(assistantMessage);
    mockResponseHandler();

    // when
    final var response = requestHandler.handleRequest(agentExecutionContext);

    // then: exactly two batched interactions emitted during handleRequest -- start, then completion
    verifyTurnLifecycleRecorded(
        AgentInstanceUpdateStatus.IDLE, new AgentMetrics(1, new TokenUsage(10, 20), 0));
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  @Test
  void shouldRecordTurnStartThenTurnCompletionWithToolCallingStatusWhenToolCalls() {
    // given
    mockConfiguration();
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
        AgentInstanceUpdateStatus.TOOL_CALLING, new AgentMetrics(1, new TokenUsage(10, 20), 2));
    verifyNoMoreInteractions(agentInstanceClient);

    // when: job completes — no additional agent instance calls
    response.onJobCompleted();
    verifyNoMoreInteractions(agentInstanceClient);
  }

  private void mockConfiguration() {
    when(agentExecutionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, USER_PROMPT, null, null, null, null));
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

  /** Verifies the two batched agent-instance interactions a chat turn produces, in order. */
  private void verifyTurnLifecycleRecorded(
      AgentInstanceUpdateStatus expectedFinalStatus, AgentMetrics expectedMetrics) {
    verify(agentInstanceClient)
        .applyTurnStart(eq(agentExecutionContext), any(), any(), any(), any(), any());
    final var turnCaptor = ArgumentCaptor.forClass(AgentConversationTurn.class);
    verify(agentInstanceClient)
        .applyTurnCompletion(
            eq(agentExecutionContext), any(), turnCaptor.capture(), any(), eq(expectedFinalStatus));
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
