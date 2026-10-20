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
import static org.mockito.Mockito.lenient;
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
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnStarted;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.lifecycle.AgentLifecycleDispatcher;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** What the handler reports to registered lifecycle listeners. */
@ExtendWith(MockitoExtension.class)
class AgentLifecycleEmissionTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

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
  void failedModelCallReportsTheCauseAndTheErrorCodeOfAConnectorException() {
    readyToConverse();
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", null));

    assertThatThrownBy(() -> handler(dispatcher(listener)).handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            thrown -> {
              assertThat(listener.modelRoundFailures).hasSize(1);
              final var roundFailure = listener.modelRoundFailures.getFirst();
              assertThat(roundFailure.cause()).isSameAs(thrown);
              assertThat(roundFailure.errorCode())
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

    assertThat(listener.modelRoundFailures)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.cause()).isSameAs(failure);
              assertThat(event.errorCode()).isNull();
            });
    assertThat(listener.jobFailures)
        .singleElement()
        .satisfies(event -> assertThat(event.errorCode()).isNull());
  }

  @Test
  void stateContributedOnTurnCompletionLandsInTheAgentContextOfTheResponse() {
    readyToConverse();
    completeTurnWithAnswer();
    listener.turnCompletedState = Map.of("status", "CLOSED");

    final var response = handler(dispatcher(listener)).handleRequest(agentExecutionContext);

    assertThat(response.responseValue()).isNotNull();
    assertThat(response.responseValue().context().properties())
        .containsEntry("lifecycle:recording", Map.of("status", "CLOSED"));
  }

  @Test
  void invalidStateContributedOnTurnCompletionIsDroppedWithoutFailingTheJob() {
    readyToConverse();
    completeTurnWithAnswer();
    listener.turnCompletedState = Map.of("status", new Object());

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
    assertThat(listener.modelRoundStarts).isEqualTo(1);
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
        .satisfies(
            execution -> {
              assertThat(execution.result().id()).isEqualTo("call_1");
              assertThat(execution.cancelled()).isFalse();
            });
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
    when(responseHandler.createResponse(any(AgentConversation.class)))
        .thenAnswer(
            i ->
                AgentResponse.builder()
                    .context(i.getArgument(0, AgentConversation.class).toAgentContext())
                    .responseMessage(answer)
                    .responseText("done")
                    .toolCalls(List.of())
                    .build());
  }

  private AgentLifecycleDispatcher dispatcher(AgentLifecycleListener... listeners) {
    return new AgentLifecycleDispatcher(List.of(listeners), new ObjectMapper(), 4096);
  }

  private void readyToConverse() {
    readyToConverse(userMessage("hello"));
  }

  private void readyToConverse(Message input) {
    when(systemPromptComposer.compose(any(), any())).thenReturn("You are a helpful assistant.");
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(input)));
    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new ReadyToConverse(INITIAL_AGENT_CONTEXT, List.of()));
  }

  private AgentSubProcessRequestHandler handler(AgentLifecycleDispatcher dispatcher) {
    return new AgentSubProcessRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        agentInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient,
        dispatcher);
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
    int modelRoundStarts;
    @Nullable Map<String, Object> turnCompletedState;
    final List<ModelRoundFailed> modelRoundFailures = new ArrayList<>();
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
        public void onModelRoundStarted(ModelRoundStarted event) {
          modelRoundStarts++;
        }

        @Override
        public Map<String, Object> onTurnCompleted(TurnCompleted event) {
          return turnCompletedState;
        }

        @Override
        public void onModelRoundFailed(ModelRoundFailed event) {
          modelRoundFailures.add(event);
        }

        @Override
        public void onJobFailed(JobFailed event) {
          jobFailures.add(event);
        }
      };
    }
  }
}
