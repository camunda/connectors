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
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.lifecycle.AgentLifecycleDispatcher;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    handler(new AgentLifecycleDispatcher(List.of(listener), 4096))
        .handleRequest(agentExecutionContext);

    assertThat(listener.discoveries).hasSize(1);
    assertThat(listener.discoveries.getFirst().toolCalls()).isEqualTo(discoveryToolCalls);
  }

  @Test
  void failedModelCallReportsTheCauseAndTheErrorCodeOfAConnectorException() {
    readyToConverse();
    when(chatModelRegistry.resolve(any())).thenReturn(chatModel);
    when(chatModel.execute(any()))
        .thenThrow(new ContentFilteredException("blocked by content filtering", null));

    assertThatThrownBy(
            () ->
                handler(new AgentLifecycleDispatcher(List.of(listener), 4096))
                    .handleRequest(agentExecutionContext))
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

    assertThatThrownBy(
            () ->
                handler(new AgentLifecycleDispatcher(List.of(listener), 4096))
                    .handleRequest(agentExecutionContext))
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

  private void readyToConverse() {
    when(systemPromptComposer.compose(any(), any())).thenReturn("You are a helpful assistant.");
    when(agentInputComposer.compose(any(), any(), any(), any()))
        .thenReturn(new CompositionResult.NextTurn(List.of(userMessage("hello"))));
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

  private static final class RecordingListener implements AgentLifecycleListener {

    final List<JobStarted> jobs = new ArrayList<>();
    final List<ToolDiscoveryDispatched> discoveries = new ArrayList<>();
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
