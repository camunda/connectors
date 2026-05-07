/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatClientImplTest {

  private static final List<ToolDefinition> TOOL_DEFINITIONS =
      List.of(ToolDefinition.builder().name("Tool").description("desc").build());

  private static final AnthropicProviderConfiguration PROVIDER_CONFIG =
      new AnthropicProviderConfiguration(
          new AnthropicConnection(
              null,
              new AnthropicAuthentication("api-key"),
              null,
              new AnthropicModel("claude", null)));

  private static final AssistantMessage ASSISTANT_MESSAGE =
      assistantMessage("hello world")
          .withUsage(TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build());

  @Mock private ChatModelApiRegistry registry;
  @Mock private ChatModelApi chatModelApi;
  @Mock private AgentExecutionContext executionContext;

  @Captor private ArgumentCaptor<ChatRequest> requestCaptor;
  @Captor private ArgumentCaptor<ChatOptions> optionsCaptor;

  private RuntimeMemory runtimeMemory;
  private ChatClientImpl chatClient;

  @BeforeEach
  void setUp() {
    runtimeMemory = new DefaultRuntimeMemory();
    runtimeMemory.addMessages(List.of(systemMessage("system"), userMessage("hi")));

    when(executionContext.provider()).thenReturn(PROVIDER_CONFIG);
    when(registry.resolve(PROVIDER_CONFIG)).thenReturn(chatModelApi);
    when(chatModelApi.complete(requestCaptor.capture(), optionsCaptor.capture(), any()))
        .thenReturn(CompletableFuture.completedFuture(new ChatResponse(ASSISTANT_MESSAGE)));

    chatClient = new ChatClientImpl(registry);
  }

  @Test
  void buildsRequestFromRuntimeMemoryAndAgentContext() {
    final var agentContext =
        AgentContext.empty().withState(AgentState.READY).withToolDefinitions(TOOL_DEFINITIONS);

    final var result = chatClient.chat(executionContext, agentContext, runtimeMemory, null);

    assertThat(result.assistantMessage()).isEqualTo(ASSISTANT_MESSAGE);
    assertThat(requestCaptor.getValue().messages())
        .containsExactlyElementsOf(runtimeMemory.filteredMessages());
    assertThat(requestCaptor.getValue().toolDefinitions())
        .containsExactlyElementsOf(TOOL_DEFINITIONS);
    assertThat(requestCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void incrementsAgentContextMetrics() {
    final var agentContext =
        AgentContext.empty()
            .withState(AgentState.READY)
            .withToolDefinitions(TOOL_DEFINITIONS)
            .withMetrics(
                AgentMetrics.empty()
                    .withModelCalls(2)
                    .withTokenUsage(
                        TokenUsage.builder().inputTokenCount(5).outputTokenCount(7).build()));

    final var result = chatClient.chat(executionContext, agentContext, runtimeMemory, null);

    assertThat(result.agentContext().metrics().modelCalls()).isEqualTo(3);
    assertThat(result.agentContext().metrics().tokenUsage())
        .isEqualTo(TokenUsage.builder().inputTokenCount(15).outputTokenCount(27).build());
  }

  @Test
  void translatesJsonResponseFormatConfiguration() {
    when(executionContext.response())
        .thenReturn(
            new OutboundConnectorResponseConfiguration(
                new JsonResponseFormatConfiguration(Map.of("type", "object"), "MySchema"), false));

    chatClient.chat(
        executionContext, agentContextWithTools(), runtimeMemory, ChatStreamListener.NOOP);

    final var responseFormat = requestCaptor.getValue().responseFormat();
    assertThat(responseFormat).isInstanceOf(JsonResponseFormatConfiguration.class);
    final var json = (JsonResponseFormatConfiguration) responseFormat;
    assertThat(json.schemaName()).isEqualTo("MySchema");
    assertThat(json.schema()).containsEntry("type", "object");
  }

  @Test
  void passesThroughTextResponseFormatConfiguration() {
    when(executionContext.response())
        .thenReturn(
            new OutboundConnectorResponseConfiguration(
                new TextResponseFormatConfiguration(false), false));

    chatClient.chat(
        executionContext, agentContextWithTools(), runtimeMemory, ChatStreamListener.NOOP);

    assertThat(requestCaptor.getValue().responseFormat())
        .isInstanceOf(TextResponseFormatConfiguration.class);
  }

  @Test
  void leavesResponseFormatNullWhenResponseConfigurationMissing() {
    when(executionContext.response()).thenReturn((ResponseConfiguration) null);

    chatClient.chat(
        executionContext, agentContextWithTools(), runtimeMemory, ChatStreamListener.NOOP);

    assertThat(requestCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void unwrapsCompletionExceptionFromUnderlyingApi() {
    final var cause = new ConnectorException("MODEL_ERROR", "boom");
    when(chatModelApi.complete(any(), any(), any()))
        .thenReturn(CompletableFuture.failedFuture(cause));

    assertThatThrownBy(
            () ->
                chatClient.chat(
                    executionContext,
                    agentContextWithTools(),
                    runtimeMemory,
                    ChatStreamListener.NOOP))
        .isSameAs(cause);
  }

  private static AgentContext agentContextWithTools() {
    return AgentContext.empty().withState(AgentState.READY).withToolDefinitions(TOOL_DEFINITIONS);
  }
}
