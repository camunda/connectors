/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentResponseCreated;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnStarted;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.lifecycle.AgentLifecycleDispatcher;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** What the handler reports to registered lifecycle listeners. */
@ExtendWith(MockitoExtension.class)
class AgentLifecycleEmissionTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final Instant JOB_STARTED_AT = Instant.parse("2026-10-19T09:00:00Z");

  private static final RuntimeTools RUNTIME_TOOLS =
      new RuntimeTools(
          List.of(),
          TestObjectMapperSupplier.INSTANCE,
          new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE));

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

  private final RecordingListener listener = new RecordingListener();

  @BeforeEach
  void setUp() {
    lenient()
        .doReturn(new InProcessConversationStore())
        .when(conversationStoreRegistry)
        .getConversationStore(any(), any(AgentContext.class));
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
    lenient().doReturn(null).when(agentExecutionContext).userPrompt();
  }

  @Test
  void toolDiscoveryEventCarriesTheDispatchedToolCalls() {
    final var discoveryToolCalls =
        List.of(
            ToolCall.builder().id("discovery_1").name("AGatewayTool").build(),
            ToolCall.builder().id("discovery_2").name("AnotherGatewayTool").build());
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new DiscoverTools(
                AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build(),
                discoveryToolCalls));

    handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(listener.discoveries).hasSize(1);
    assertThat(listener.discoveries.getFirst().toolCalls()).isEqualTo(discoveryToolCalls);
  }

  @Test
  void jobStartedCarriesTheTimeOfTheClockOfTheHandler() {
    readyToConverse();
    completeTurnWithAnswer();

    handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(listener.jobs)
        .singleElement()
        .satisfies(job -> assertThat(job.startedAt()).isEqualTo(JOB_STARTED_AT));
  }

  @Test
  void failedModelCallReportsTheCauseAndTheErrorCodeOfAConnectorException() {
    readyToConverse();
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", null));

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            thrown -> {
              assertThat(listener.turnFailures).hasSize(1);
              final var turnFailure = listener.turnFailures.getFirst();
              assertThat(turnFailure.iterationKey()).isEqualTo(1);
              assertThat(turnFailure.cause()).isSameAs(thrown);
              assertThat(turnFailure.errorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED);

              assertThat(listener.jobFailures).hasSize(1);
              assertThat(listener.jobFailures.getFirst().cause()).isSameAs(thrown);
              assertThat(listener.jobFailures.getFirst().errorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED);
            });
  }

  @Test
  void failedModelCallWithoutAnErrorCodeReportsOnlyTheCause() {
    readyToConverse();
    final var failure = new IllegalStateException("provider exploded");
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any())).thenThrow(failure);

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isSameAs(failure);

    assertThat(listener.turnFailures)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.cause()).isSameAs(failure);
          assertThat(event.errorCode()).isNull();
        });
    assertThat(listener.jobFailures)
        .singleElement()
        .satisfies(event -> assertThat(event.errorCode()).isNull());
  }

  @Test
  void stateContributedOnAgentResponseCreationLandsInTheAgentContextOfTheResponse() {
    readyToConverse();
    completeTurnWithAnswer();
    listener.agentResponseState = Map.of("status", "CLOSED");

    final var response = handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(response.responseValue()).isNotNull();
    assertThat(response.responseValue().context().properties())
        .containsEntry("lifecycle:recording", Map.of("status", "CLOSED"));
  }

  @Test
  void invalidStateContributedOnAgentResponseCreationIsDroppedWithoutFailingTheJob() {
    readyToConverse();
    completeTurnWithAnswer();
    listener.agentResponseState = Map.of("status", new Object());

    final var response = handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(response.responseValue()).isNotNull();
    assertThat(response.responseValue().context().properties()).isEmpty();
  }

  @Test
  void failureWhileDerivingTheTurnStartedEventDoesNotFailTheJob() {
    readyToConverseWithToolResultThatFailsWhenInspected();
    completeTurnWithAnswer();

    final var response = handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(response.responseValue()).isNotNull();
    assertThat(listener.turnStarts).isEmpty();
    assertThat(listener.turnCompletions).hasSize(1);
  }

  @Test
  void turnStartedEventIsNotDerivedWithoutAListener() {
    readyToConverseWithToolResultThatFailsWhenInspected();
    completeTurnWithAnswer();

    final var response =
        handler(new AgentLifecycleDispatcher(List.of(), new ObjectMapper(), 4096))
            .handleRequest(agentExecutionContext);

    assertThat(response.responseValue()).isNotNull();
  }

  @Test
  void turnStartedEventCarriesTheToolExecutionsOfTheTurn() {
    readyToConverse(
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder()
                    .id("call_1")
                    .name("getWeather")
                    .content("Sunny")
                    .build())));
    completeTurnWithAnswer();

    handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(listener.turnStarts).hasSize(1);
    assertThat(listener.turnStarts.getFirst().toolExecutions())
        .singleElement()
        .satisfies(execution -> {
          assertThat(execution.result().id()).isEqualTo("call_1");
          assertThat(execution.call()).isNull();
          assertThat(execution.cancelled()).isFalse();
          assertThat(execution.startedAt()).isNull();
        });
  }

  @Test
  void turnStartedEventCarriesTheMessagesAndTheInputOfTheTurn() {
    readyToConverse();
    completeTurnWithAnswer();

    handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(listener.turnStarts)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.iterationKey()).isEqualTo(1);
          assertThat(event.chatModel()).isEqualTo(TEST_CHAT_MODEL);
          assertThat(event.inputMessages()).hasSize(1);
          assertThat(event.messages()).containsAll(event.inputMessages());
          assertThat(event.toolExecutions()).isEmpty();
        });
    assertThat(listener.turnCompletions)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.iterationKey()).isEqualTo(1);
          assertThat(event.anotherTurnFollows()).isFalse();
        });
  }

  @Test
  void runtimeToolCallOfATurnIsReportedAsToolExecutionOfTheNextTurn() {
    final var toolStartedAt = OffsetDateTime.parse("2026-10-19T09:00:01Z");
    final var toolCompletedAt = OffsetDateTime.parse("2026-10-19T09:00:02Z");
    final var runtimeToolDefinition =
        ToolDefinition.runtime("acme_note", "Takes a note", Map.of("type", "object"));
    final var runtimeTools = mock(RuntimeTools.class);
    when(runtimeTools.enabledDefinitions(any(), any())).thenReturn(List.of(runtimeToolDefinition));
    when(runtimeTools.execute(any(), any(), any()))
        .thenAnswer(invocation ->
            invocation.<List<ToolCall>>getArgument(2).stream()
                .map(call ->
                    new RuntimeToolExecution(
                        call, RuntimeToolResult.success("Saved"), toolStartedAt, toolCompletedAt))
                .toList());

    readyToConverse();
    final var runtimeToolMessage =
        assistantMessage(
            "I take a note",
            List.of(
                ToolCall.builder()
                    .id("call_rt")
                    .name("acme_note")
                    .arguments(Map.of("text", "hello"))
                    .build()));
    final var answer = assistantMessage("done");
    final var firstMetrics =
        new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 5), 0, Duration.ZERO);
    final var secondMetrics =
        new AgentMetrics(1, new AgentMetrics.TokenUsage(20, 7), 0, Duration.ZERO);
    // the composer assembles the user input of the first turn, then the result of the runtime call
    when(agentInputComposer.assemble(any()))
        .thenReturn(
            new TurnInput(List.of(userMessage("hello"))),
            new TurnInput(
                List.of(
                    ToolCallResultMessage.builder()
                        .results(
                            List.of(
                                ToolCallResultContent.builder()
                                    .id("call_rt")
                                    .name("acme_note")
                                    .content(List.of(TextContent.textContent("Saved")))
                                    .completedAt(toolCompletedAt)
                                    .build()))
                        .build())));
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenReturn(
            new ChatResult.Completed(runtimeToolMessage, firstMetrics),
            new ChatResult.Completed(answer, secondMetrics));
    stubResponseHandler(answer);

    handler(dispatcher(listener), runtimeTools).handleRequest(agentExecutionContext);

    assertThat(listener.turnStarts).hasSize(2);
    final var first = listener.turnStarts.get(0);
    final var second = listener.turnStarts.get(1);
    assertThat(first.iterationKey()).isEqualTo(1);
    assertThat(first.toolExecutions()).isEmpty();
    assertThat(second.iterationKey()).isEqualTo(2);
    assertThat(second.toolDefinitions()).contains(runtimeToolDefinition);
    assertThat(second.toolExecutions())
        .singleElement()
        .satisfies(execution -> {
          assertThat(execution.call()).isNotNull();
          assertThat(execution.call().id()).isEqualTo("call_rt");
          assertThat(execution.call().resultSource()).isEqualTo(ToolCall.ResultSource.RUNTIME);
          assertThat(execution.result().id()).isEqualTo("call_rt");
          assertThat(execution.startedAt()).isEqualTo(toolStartedAt);
          assertThat(execution.cancelled()).isFalse();
        });

    assertThat(listener.turnCompletions).hasSize(2);
    assertThat(listener.turnCompletions.get(0).iterationKey()).isEqualTo(1);
    assertThat(listener.turnCompletions.get(0).anotherTurnFollows()).isTrue();
    assertThat(listener.turnCompletions.get(0).assistantMessage().toolCalls())
        .singleElement()
        .satisfies(call -> assertThat(call.kind()).isEqualTo(ToolKind.RUNTIME));
    assertThat(listener.turnCompletions.get(1).iterationKey()).isEqualTo(2);
    assertThat(listener.turnCompletions.get(1).anotherTurnFollows()).isFalse();

    assertThat(listener.agentResponses)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.iterationKey()).isEqualTo(2);
          assertThat(event.finalAnswer()).isTrue();
          assertThat(event.jobMetrics().modelCalls()).isEqualTo(2);
          assertThat(event.jobMetrics().tokenUsage())
              .isEqualTo(new AgentMetrics.TokenUsage(30, 12));
        });
  }

  @Test
  void toolDiscoveryDispatchIsReportedAfterTheFencedHistoryWrite() {
    final var discoveryContext = AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build();
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new DiscoverTools(
                discoveryContext, List.of(ToolCall.builder().id("d1").name("Gateway").build())));
    final var observer = mock(AgentJobObserver.class);

    handler(dispatcher(listenerOf(observer))).handleRequest(agentExecutionContext);

    final InOrder inOrder = inOrder(agentInstanceClient, observer);
    inOrder.verify(agentInstanceClient).applyToolDiscoveryStart(eq(agentExecutionContext), any());
    inOrder.verify(observer).onToolDiscoveryDispatched(any(ToolDiscoveryDispatched.class));
  }

  @Test
  void supersededToolDiscoveryWriteReportsNoDispatch() {
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new DiscoverTools(
                AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build(),
                List.of(ToolCall.builder().id("d1").name("Gateway").build())));
    doThrow(superseded())
        .when(agentInstanceClient)
        .applyToolDiscoveryStart(eq(agentExecutionContext), any());

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isInstanceOf(ConnectorRetryException.class);

    assertThat(listener.discoveries).isEmpty();
    assertThat(listener.jobFailures)
        .singleElement()
        .satisfies(event ->
            assertThat(event.errorCode())
                .isEqualTo(AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_SUPERSEDED));
  }

  @Test
  void turnStartIsReportedAfterTheFencedHistoryWrite() {
    readyToConverse();
    completeTurnWithAnswer();
    final var observer = mock(AgentJobObserver.class);

    handler(dispatcher(listenerOf(observer))).handleRequest(agentExecutionContext);

    final InOrder inOrder = inOrder(agentInstanceClient, observer);
    inOrder
        .verify(agentInstanceClient)
        .applyTurnStart(eq(agentExecutionContext), any(), any(), any(), any(), any(), any());
    inOrder.verify(observer).onTurnStarted(any(TurnStarted.class));
    inOrder.verify(observer).onTurnCompleted(any(TurnCompleted.class));
    inOrder.verify(observer).onAgentResponseCreated(any(AgentResponseCreated.class));
  }

  @Test
  void supersededTurnStartWriteReportsNoTurnStart() {
    readyToConverse();
    doThrow(superseded())
        .when(agentInstanceClient)
        .applyTurnStart(eq(agentExecutionContext), any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isInstanceOf(ConnectorRetryException.class);

    assertThat(listener.turnStarts).isEmpty();
    assertThat(listener.jobFailures)
        .singleElement()
        .satisfies(event ->
            assertThat(event.errorCode())
                .isEqualTo(AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_SUPERSEDED));
    verifyNoInteractions(chatModelRegistry, chatModel);
  }

  @Test
  void modelCallLimitFailureReportsTheFailedJobWithoutATurnStart() {
    composesTurnInput(userMessage("hello"));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(
            new ReadyToConverse(
                AgentContext.builder()
                    .state(AgentState.READY)
                    .toolDefinitions(TOOL_DEFINITIONS)
                    .metrics(new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0))
                    .build(),
                List.of()));
    doReturn(
            new AgentConfiguration(
                TEST_CHAT_MODEL,
                TEST_SYSTEM_PROMPT,
                new PromptConfiguration.UserPromptConfiguration("user prompt", List.of()),
                null,
                new LimitsConfiguration(1),
                null,
                null))
        .when(agentExecutionContext)
        .configuration();

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isInstanceOf(ConnectorException.class);

    assertThat(listener.turnStarts).isEmpty();
    assertThat(listener.jobFailures)
        .singleElement()
        .satisfies(event ->
            assertThat(event.errorCode())
                .isEqualTo(AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED));
    verifyNoInteractions(agentInstanceClient);
  }

  private static ConnectorRetryException superseded() {
    return ConnectorRetryException.builder()
        .errorCode(AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_SUPERSEDED)
        .message("superseded")
        .retries(0)
        .build();
  }

  private static AgentLifecycleListener listenerOf(AgentJobObserver observer) {
    return new AgentLifecycleListener() {
      @Override
      public String id() {
        return "observed";
      }

      @Override
      public AgentJobObserver onJobStarted(JobStarted event) {
        return observer;
      }
    };
  }

  private void readyToConverseWithToolResultThatFailsWhenInspected() {
    final var failing =
        new ToolCallResultContent(
            "call_1", "getWeather", List.of(), null, null, new FailingProperties());
    readyToConverse(ToolCallResultMessage.builder().results(List.of(failing)).build());
  }

  private void completeTurnWithAnswer() {
    final var answer = assistantMessage("done");
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenReturn(
            new ChatResult.Completed(
                answer, new AgentMetrics(1, new AgentMetrics.TokenUsage(1, 2), 0, Duration.ZERO)));
    stubResponseHandler(answer);
  }

  private void stubResponseHandler(AssistantMessage answer) {
    when(responseHandler.createResponse(any(AgentConversation.class)))
        .thenAnswer(i ->
            AgentResponse.builder()
                .context(i.getArgument(0, AgentConversation.class).toAgentContext())
                .responseMessage(answer)
                .responseText("done")
                .toolCalls(List.of())
                .build());
  }

  /** The composer resolves the turn without tool calls and assembles the given input messages. */
  private void composesTurnInput(Message... inputMessages) {
    when(agentInputComposer.resolve(any(), any(), any(), any()))
        .thenAnswer(invocation ->
            CompositionResult.Ready.of(
                invocation.getArgument(1, AgentContext.class), ToolCallBatch.of(List.of())));
    lenient()
        .when(agentInputComposer.assemble(any()))
        .thenReturn(new TurnInput(List.of(inputMessages)));
  }

  private AgentLifecycleDispatcher dispatcher(AgentLifecycleListener... listeners) {
    return new AgentLifecycleDispatcher(List.of(listeners), new ObjectMapper(), 4096);
  }

  private void readyToConverse() {
    readyToConverse(userMessage("hello"));
  }

  private void readyToConverse(Message input) {
    when(systemPromptComposer.compose(any(), any())).thenReturn("You are a helpful assistant.");
    composesTurnInput(input);
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
  }

  private AgentSubProcessRequestHandler handler(AgentLifecycleDispatcher dispatcher) {
    return handler(dispatcher, RUNTIME_TOOLS);
  }

  private AgentSubProcessRequestHandler handler(
      AgentLifecycleDispatcher dispatcher, RuntimeTools runtimeTools) {
    return new AgentSubProcessRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        agentInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient,
        runtimeTools,
        new ToolCallAdmission(new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE)),
        new LocalToolCallExecutor(runtimeTools, TestDocuments.objectHandleRenderer()),
        TestDocuments.documentExpander(),
        dispatcher,
        Clock.fixed(JOB_STARTED_AT, ZoneOffset.UTC));
  }

  /** Tool result properties that throw when the interrupted flag is looked up. */
  private static final class FailingProperties extends HashMap<String, Object> {

    @Override
    public Object get(Object key) {
      throw new IllegalStateException("tool result content");
    }
  }

  private static final class RecordingListener implements AgentLifecycleListener {

    final List<JobStarted> jobs = new ArrayList<>();
    final List<ToolDiscoveryDispatched> discoveries = new ArrayList<>();
    final List<TurnStarted> turnStarts = new ArrayList<>();
    final List<TurnCompleted> turnCompletions = new ArrayList<>();
    final List<TurnFailed> turnFailures = new ArrayList<>();
    final List<AgentResponseCreated> agentResponses = new ArrayList<>();
    @Nullable Map<String, Object> agentResponseState;
    final List<JobFailed> jobFailures = new ArrayList<>();

    @Override
    public String id() {
      return "recording";
    }

    @Override
    public AgentJobObserver onJobStarted(JobStarted event) {
      jobs.add(event);
      return new AgentJobObserver() {
        @Override
        public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched event) {
          discoveries.add(event);
          return null;
        }

        @Override
        public void onTurnStarted(TurnStarted event) {
          turnStarts.add(event);
        }

        @Override
        public void onTurnCompleted(TurnCompleted event) {
          turnCompletions.add(event);
        }

        @Override
        public void onTurnFailed(TurnFailed event) {
          turnFailures.add(event);
        }

        @Override
        public Map<String, Object> onAgentResponseCreated(AgentResponseCreated event) {
          agentResponses.add(event);
          return agentResponseState;
        }

        @Override
        public void onJobFailed(JobFailed event) {
          jobFailures.add(event);
        }
      };
    }
  }
}
