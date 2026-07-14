/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JChatModelApiTest {

  @Mock private AgentExecutionContext executionContext;
  @Mock private ConversationSnapshot snapshot;
  @Mock private Langchain4JAiFrameworkAdapter adapter;

  @Test
  void wrapsAdapterResponseAsResult() {
    final var msg = AssistantMessage.builder().content(List.of(textContent("hi"))).build();
    final var metrics = new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0);
    final var response = mock(Langchain4JAiFrameworkChatResponse.class);
    when(response.assistantMessage()).thenReturn(msg);
    when(response.metrics()).thenReturn(metrics);
    when(adapter.executeMeasuringTime(any(), any())).thenReturn(response);

    final var api = new Langchain4JChatModelApi(adapter);
    final var result = api.call(new ChatModelRequest(executionContext, snapshot));

    assertThat(result).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(result).isEqualTo(new ChatModelResult.Completed(msg, metrics));
  }

  @Test
  void capabilitiesReturnsUniformConservativeBridgeProfileWithoutCallingResolver() {
    final var api = new Langchain4JChatModelApi(adapter);

    final var capabilities = api.capabilities();

    assertThat(capabilities.userMessageModalities())
        .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
    assertThat(capabilities.toolResultModalities()).isEmpty();
    assertThat(capabilities.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(capabilities).isInstanceOf(CoreModelCapabilities.class);
    final var core = (CoreModelCapabilities) capabilities;
    assertThat(core.contextWindow()).isNull();
    assertThat(core.maxOutputTokens()).isNull();
  }

  @Test
  void factorySupportsAnyProviderChatModelApiConfigurationAtLowPrecedence() {
    final var factory = new Langchain4JChatModelApiFactory(adapter);

    final var configuration =
        new ProviderChatModelApiConfiguration(
            new AnthropicProviderConfiguration(
                new AnthropicConnection(
                    null,
                    new AnthropicAuthentication("api-key"),
                    null,
                    new AnthropicModel("claude", null))));

    assertThat(factory.supports(configuration)).isTrue();
    assertThat(factory.getOrder()).isEqualTo(Langchain4JChatModelApiFactory.ORDER);
    assertThat(factory.create(configuration)).isInstanceOf(Langchain4JChatModelApi.class);
  }
}
