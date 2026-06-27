/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.SandboxSessionFactory;
import io.camunda.connector.agenticai.sandbox.SandboxSessionFactoryImpl;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolContext;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolExecutor;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolHandler;
import io.camunda.connector.agenticai.sandbox.internaltool.InternalToolRegistry;
import io.camunda.connector.agenticai.sandbox.provider.fake.InMemorySandboxProvider;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the in-process tool sub-loop in {@link BaseAgentRequestHandler}. Uses {@link
 * OutboundConnectorAgentRequestHandler} as the concrete subclass (simpler — no AHSP complexity).
 */
@ExtendWith(MockitoExtension.class)
class BaseAgentRequestHandlerSubLoopTest {

  private static final String INTERNAL_TOOL_NAME = "test_internal_tool";
  private static final String EXTERNAL_TOOL_NAME = "getWeather";
  private static final TokenUsage TOKEN_USAGE = new TokenUsage(5, 10);

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final UserPromptConfiguration USER_PROMPT =
      new UserPromptConfiguration("hi", List.of());

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentConversationTurnInputComposer agentInputComposer;
  @Mock private AiFrameworkAdapter<?> framework;
  @Mock private SystemPromptComposer systemPromptComposer;
  @Mock private AgentInstanceClient agentInstanceClient;
  @Mock private OutboundConnectorAgentExecutionContext executionContext;

  private OutboundConnectorAgentRequestHandler requestHandler;
  private StubInternalToolHandler stubHandler;

  /** A simple stub InternalToolHandler used in tests. */
  private static class StubInternalToolHandler implements InternalToolHandler {
    private int callCount = 0;
    private final String toolName;

    StubInternalToolHandler(String name) {
      this.toolName = name;
    }

    @Override
    public String name() {
      return toolName;
    }

    @Override
    public ToolDefinition definition() {
      return ToolDefinition.builder().name(toolName).description("A stub internal tool").build();
    }

    @Override
    public ToolCallResult execute(
        ToolCall toolCall, SandboxSession session, InternalToolContext context) {
      callCount++;
      return ToolCallResult.builder()
          .id(toolCall.id())
          .name(toolCall.name())
          .content("stub result " + callCount)
          .build();
    }

    int getCallCount() {
      return callCount;
    }
  }

  @BeforeEach
  void setUp() {
    stubHandler = new StubInternalToolHandler(INTERNAL_TOOL_NAME);
    var internalToolRegistry = new InternalToolRegistry(List.of(stubHandler));
    var internalToolExecutor = new InternalToolExecutor(internalToolRegistry);

    // SandboxSessionFactory backed by real InMemorySandboxProvider (always creates session)
    var sandboxProvider = new InMemorySandboxProvider();
    SandboxSessionFactory sandboxSessionFactory =
        (ctx, agentCtx) -> Optional.of(sandboxProvider.create(SandboxSpec.defaults()));

    var responseHandler =
        new AgentResponseHandlerImpl(
            new ObjectMapper(),
            new GatewayToolHandlerRegistryImpl(List.of()),
            internalToolRegistry);

    requestHandler =
        new OutboundConnectorAgentRequestHandler(
            agentInitializer,
            conversationStoreRegistry,
            agentInputComposer,
            framework,
            systemPromptComposer,
            responseHandler,
            agentInstanceClient,
            internalToolRegistry,
            internalToolExecutor,
            sandboxSessionFactory,
            new SkillResolver());

    // Common setup
    doReturn(new InProcessConversationStore())
        .when(conversationStoreRegistry)
        .getConversationStore(any(), any());
    when(systemPromptComposer.compose(any(), any())).thenReturn("");
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(userMessage("hi"))));
    when(executionContext.configuration())
        .thenReturn(new AgentConfiguration(null, null, USER_PROMPT, null, null, null, null, null));
    when(agentInitializer.initializeAgent(executionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
  }

  private AssistantMessage withInternalToolCall(String id) {
    var toolCall = ToolCall.builder().id(id).name(INTERNAL_TOOL_NAME).arguments(Map.of()).build();
    return assistantMessage("calling internal tool", List.of(toolCall));
  }

  private AssistantMessage withExternalToolCall(String id) {
    var toolCall = ToolCall.builder().id(id).name(EXTERNAL_TOOL_NAME).arguments(Map.of()).build();
    return assistantMessage("calling external tool", List.of(toolCall));
  }

  private AssistantMessage withInternalAndExternalToolCall(String internalId, String externalId) {
    var internal =
        ToolCall.builder().id(internalId).name(INTERNAL_TOOL_NAME).arguments(Map.of()).build();
    var external =
        ToolCall.builder().id(externalId).name(EXTERNAL_TOOL_NAME).arguments(Map.of()).build();
    return assistantMessage("calling internal and external tools", List.of(internal, external));
  }

  private AssistantMessage finalAnswer(String text) {
    return assistantMessage(text);
  }

  private record TestChatResponse(AssistantMessage assistantMessage, AgentMetrics metrics)
      implements AiFrameworkChatResponse<Void> {
    TestChatResponse(AssistantMessage assistantMessage, TokenUsage tokenUsage) {
      this(
          assistantMessage,
          new AgentMetrics(
              1,
              tokenUsage,
              assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size()));
    }

    @Override
    public AiFrameworkChatResponse<Void> withExecutionTimeMetrics(Duration executionTime) {
      return new TestChatResponse(assistantMessage, metrics.withExecutionTime(executionTime));
    }

    @Override
    public Void rawChatResponse() {
      return null;
    }
  }

  @Test
  void pureInternalTurns_loopsNPlusOneTimes_andCompletesNormally() {
    // LLM: call internal tool → call internal tool → final answer
    doReturn(new TestChatResponse(withInternalToolCall("t1"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(withInternalToolCall("t2"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(finalAnswer("done"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);

    // 3 framework calls total (2 internal + 1 final)
    verify(framework, times(3)).executeMeasuringTime(any(), any());
    // 2 internal tool executions
    assertThat(stubHandler.getCallCount()).isEqualTo(2);
    // AgentResponse.toolCalls is empty (no external tools)
    assertThat(response.agentResponse().toolCalls()).isEmpty();
    assertThat(response.agentResponse().responseText()).isEqualTo("done");
  }

  @Test
  void pureExternalToolCall_doesNotEnterSubLoop() {
    // LLM: call external tool only
    doReturn(new TestChatResponse(withExternalToolCall("e1"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);

    // Only 1 framework call
    verify(framework, times(1)).executeMeasuringTime(any(), any());
    // No internal tool executions
    assertThat(stubHandler.getCallCount()).isEqualTo(0);
    // AgentResponse.toolCalls has the external tool call
    assertThat(response.agentResponse().toolCalls()).hasSize(1);
    assertThat(response.agentResponse().toolCalls().get(0).metadata().name())
        .isEqualTo(EXTERNAL_TOOL_NAME);
  }

  @Test
  void finalAnswer_noToolCalls_completesNormally() {
    doReturn(new TestChatResponse(finalAnswer("all done"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);

    verify(framework, times(1)).executeMeasuringTime(any(), any());
    assertThat(stubHandler.getCallCount()).isEqualTo(0);
    assertThat(response.agentResponse().toolCalls()).isEmpty();
    assertThat(response.agentResponse().responseText()).isEqualTo("all done");
  }

  @Test
  void maxInternalToolIterations_exceeded_throwsConnectorException() {
    // Set limit to 1 iteration
    when(executionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                null, null, USER_PROMPT, null, new LimitsConfiguration(10, 1), null, null, null));

    // LLM keeps returning internal tool calls forever
    doReturn(new TestChatResponse(withInternalToolCall("t1"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    assertThatThrownBy(() -> requestHandler.handleRequest(executionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(
                        AgentErrorCodes
                            .ERROR_CODE_MAXIMUM_NUMBER_OF_INTERNAL_TOOL_ITERATIONS_REACHED));
  }

  @Test
  void internalToolCalls_neverAppearInAgentResponseToolCalls() {
    // First LLM response: internal tool call
    // Second LLM response: external tool call
    doReturn(new TestChatResponse(withInternalToolCall("t1"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(withExternalToolCall("e1"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);

    // 2 LLM calls: one for internal, one for external
    verify(framework, times(2)).executeMeasuringTime(any(), any());
    // Internal tool call was executed
    assertThat(stubHandler.getCallCount()).isEqualTo(1);
    // AgentResponse only has the external tool call
    assertThat(response.agentResponse().toolCalls()).hasSize(1);
    assertThat(response.agentResponse().toolCalls().get(0).metadata().name())
        .isEqualTo(EXTERNAL_TOOL_NAME);
  }

  @Test
  void mixedTurn_internalAndExternalInOneTurn_reEntersSuccessfully() {
    // Turn 1: a single assistant message requesting BOTH an in-process and an external tool call.
    // The in-process tool runs immediately; the external call is surfaced to the AHSP and the job
    // completes. The in-process result is persisted as an open trailing turn.
    doReturn(new TestChatResponse(withInternalAndExternalToolCall("i1", "e1"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var first = requestHandler.handleRequest(executionContext);

    verify(framework, times(1)).executeMeasuringTime(any(), any());
    assertThat(stubHandler.getCallCount()).isEqualTo(1);
    assertThat(first.agentResponse().toolCalls()).hasSize(1);
    assertThat(first.agentResponse().toolCalls().get(0).metadata().name())
        .isEqualTo(EXTERNAL_TOOL_NAME);

    // Re-entry: the external tool result arrives. The conversation persisted in turn 1 ends with
    // the open trailing turn (the in-process result). Reconstruction must NOT reject it, and the
    // persisted in-process result must merge with the arriving external result as the next turn's
    // input. (Before the fix, TurnReconstructor threw here and the AI_Agent raised an incident.)
    var storedContext = first.agentResponse().context();
    when(agentInitializer.initializeAgent(executionContext))
        .thenReturn(new ReadyToConverse(storedContext, TOOL_CALL_RESULTS));
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(
            new CompositionResult.NextTurn(List.of(toolCallResultMessage(TOOL_CALL_RESULTS))));
    doReturn(new TestChatResponse(finalAnswer("here is the summary"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var second = requestHandler.handleRequest(executionContext);

    assertThat(second.agentResponse().responseText()).isEqualTo("here is the summary");
    assertThat(second.agentResponse().toolCalls()).isEmpty();
  }

  @Test
  void sandboxHandlePersistedInContextProperties_afterSubLoop() {
    doReturn(new TestChatResponse(withInternalToolCall("t1"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(finalAnswer("done"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);

    // sandboxHandle should be stored in agent context properties
    var agentContext = response.agentResponse().context();
    assertThat(agentContext.properties()).containsKey(SandboxSessionFactoryImpl.SANDBOX_HANDLE_KEY);
    var handle = agentContext.properties().get(SandboxSessionFactoryImpl.SANDBOX_HANDLE_KEY);
    assertThat(handle).isInstanceOf(SandboxHandle.class);
  }

  @Test
  void multiTurnConversation_metricsCountAllLlmCalls() {
    // 2 internal turns + final answer = 3 LLM calls
    doReturn(new TestChatResponse(withInternalToolCall("t1"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(withInternalToolCall("t2"), TOKEN_USAGE))
        .doReturn(new TestChatResponse(finalAnswer("done"), TOKEN_USAGE))
        .when(framework)
        .executeMeasuringTime(any(), any());

    var response = requestHandler.handleRequest(executionContext);
    var context = response.agentResponse().context();

    // 3 model calls tracked in the returned context (base = 0 + invocation = 3)
    assertThat(context.metrics().modelCalls()).isEqualTo(3);
  }
}
