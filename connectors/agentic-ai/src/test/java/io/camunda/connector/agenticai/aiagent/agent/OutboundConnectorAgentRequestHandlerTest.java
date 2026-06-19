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
import static org.mockito.Mockito.doReturn;
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
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobCompletionFailure;
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

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private ConversationTurnComposer agentInputComposer;
  @Mock private AiFrameworkAdapter<?> framework;
  @Mock private SystemPromptComposer systemPromptComposer;
  @Mock private AgentResponseHandler responseHandler;
  @Mock private AgentInstanceClient agentInstanceClient;
  @Mock private OutboundConnectorAgentExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<ConversationSnapshot> snapshotCaptor;

  @InjectMocks private OutboundConnectorAgentRequestHandler requestHandler;

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

    verifyNoInteractions(agentInputComposer, framework, responseHandler);
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

    verifyNoInteractions(agentInputComposer, framework, responseHandler);
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
    assertThat(snapshotCaptor.getValue().messages()).containsExactly(SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
    assertThat(agentResponse).isNotNull();
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

    assertThat(snapshotCaptor.getValue().messages()).containsExactly(SYSTEM_MESSAGE, USER_MESSAGE);

    var agentResponse = response.agentResponse();
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
                  .isEqualTo("No user message content available to start the conversation.");
            });

    verifyNoInteractions(framework, agentInstanceClient);
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
                null, null, USER_PROMPT, null, new LimitsConfiguration(2), null, null));

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
    verifyNoInteractions(framework);
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
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
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
            eq(AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING)));
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
                    .delta(new AgentMetrics(1, new TokenUsage(10, 20), 0))
                    .build()));
  }

  private void mockConfiguration() {
    when(agentExecutionContext.configuration())
        .thenReturn(new AgentConfiguration(null, null, USER_PROMPT, null, null, null, null));
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

  private void mockFrameworkExecution(AssistantMessage assistantMessage) {
    doReturn(
            new TestFrameworkChatResponse(
                assistantMessage, new TokenUsage(10, 20), Map.of("message", assistantMessage)))
        .when(framework)
        .executeChatRequest(eq(agentExecutionContext), snapshotCaptor.capture());
  }

  private record TestFrameworkChatResponse(
      AssistantMessage assistantMessage, TokenUsage tokenUsage, Map<String, Object> rawChatResponse)
      implements AiFrameworkChatResponse<Map<String, Object>> {}
}
