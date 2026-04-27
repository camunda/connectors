/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {

  private final ChatModelProviderRegistry registry = mock(ChatModelProviderRegistry.class);
  private final ChatModelFactory chatModelFactory = new ChatModelFactoryImpl(registry);

  @Test
  @SuppressWarnings("unchecked")
  void delegatesToProviderResolvedFromRegistry() {
    final ProviderConfiguration providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("api-key"),
                null,
                new AnthropicModel("claude", null)));
    final var expectedChatModel = mock(ChatModel.class);
    final ChatModelProvider<ProviderConfiguration> provider = mock(ChatModelProvider.class);
    when(registry.getChatModelProvider(providerConfig)).thenReturn(provider);
    when(provider.createChatModel(providerConfig)).thenReturn(expectedChatModel);

    final var chatModel = chatModelFactory.createChatModel(providerConfig);

    assertThat(chatModel).isSameAs(expectedChatModel);
    verify(registry).getChatModelProvider(providerConfig);
    verify(provider).createChatModel(providerConfig);
  }
}
