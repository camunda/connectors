/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ResponseFormat;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
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

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.empty().withState(AgentState.READY).withToolDefinitions(TOOL_DEFINITIONS);

  private static final AnthropicProviderConfiguration PROVIDER_CONFIG =
      new AnthropicProviderConfiguration(
          new AnthropicConnection(
              null,
              new AnthropicAuthentication("api-key"),
              null,
              new AnthropicModel("claude", null)));

  @Mock private ChatModelApiRegistry registry;
  @Mock private ChatModelApi chatModelApi;
  @Mock private AgentExecutionContext executionContext;
  @Mock private ChatResponse chatResponse;

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
        .thenReturn(CompletableFuture.completedFuture(chatResponse));

    chatClient = new ChatClientImpl(registry);
  }

  @Test
  void buildsRequestFromRuntimeMemoryAndAgentContext() {
    final var future = chatClient.chat(executionContext, AGENT_CONTEXT, runtimeMemory, null);

    assertThat(future).isCompleted();
    assertThat(future.join()).isSameAs(chatResponse);
    assertThat(requestCaptor.getValue().messages())
        .containsExactlyElementsOf(runtimeMemory.filteredMessages());
    assertThat(requestCaptor.getValue().tools()).containsExactlyElementsOf(TOOL_DEFINITIONS);
    assertThat(requestCaptor.getValue().systemPrompt()).isNull();
  }

  @Test
  void translatesJsonResponseFormatConfiguration() {
    when(executionContext.response())
        .thenReturn(
            new OutboundConnectorResponseConfiguration(
                new JsonResponseFormatConfiguration(Map.of("type", "object"), "MySchema"), false));

    chatClient.chat(executionContext, AGENT_CONTEXT, runtimeMemory, ChatStreamListener.NOOP);

    final var responseFormat = optionsCaptor.getValue().responseFormat();
    assertThat(responseFormat).isInstanceOf(ResponseFormat.Json.class);
    final var json = (ResponseFormat.Json) responseFormat;
    assertThat(json.schemaName()).isEqualTo("MySchema");
    assertThat(json.schema()).containsEntry("type", "object");
  }

  @Test
  void leavesResponseFormatNullForTextConfiguration() {
    when(executionContext.response())
        .thenReturn(
            new OutboundConnectorResponseConfiguration(
                new TextResponseFormatConfiguration(false), false));

    chatClient.chat(executionContext, AGENT_CONTEXT, runtimeMemory, ChatStreamListener.NOOP);

    assertThat(optionsCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void leavesResponseFormatNullWhenResponseConfigurationMissing() {
    when(executionContext.response()).thenReturn((ResponseConfiguration) null);

    chatClient.chat(executionContext, AGENT_CONTEXT, runtimeMemory, ChatStreamListener.NOOP);

    assertThat(optionsCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void usesNoopListenerWhenCallerPassesNull() {
    chatClient.chat(executionContext, AGENT_CONTEXT, runtimeMemory, null);

    // no NPE on the underlying ChatModelApi means the null listener was substituted with NOOP
  }
}
