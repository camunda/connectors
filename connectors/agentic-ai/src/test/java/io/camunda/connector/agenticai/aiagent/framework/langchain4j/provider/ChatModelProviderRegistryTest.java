/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatModelProviderRegistryTest {

  private ChatModelProviderRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ChatModelProviderRegistry();
  }

  @Test
  void shouldRegisterProvider() {
    // given
    var provider = createMockProvider("test-provider");

    // when
    registry.registerProvider(provider);

    // then - should not throw
  }

  @Test
  void shouldFailToRegisterProviderWithDuplicateType() {
    // given
    var provider1 = createMockProvider("test-provider");
    var provider2 = createMockProvider("test-provider");

    // when
    registry.registerProvider(provider1);

    // then
    assertThatThrownBy(() -> registry.registerProvider(provider2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chat model provider with type 'test-provider' is already registered");
  }

  @Test
  void shouldCreateChatModelFromBuiltInConfiguration() {
    // given
    var mockChatModel = mock(ChatModel.class);
    var provider = createMockProvider(AnthropicProviderConfiguration.ANTHROPIC_ID);
    var config = mock(AnthropicProviderConfiguration.class);
    
    when(provider.supports(config)).thenReturn(true);
    when(provider.createChatModel(config)).thenReturn(mockChatModel);

    registry.registerProvider(provider);

    // when
    var result = registry.createChatModel(config);

    // then
    assertThat(result).isSameAs(mockChatModel);
  }

  @Test
  void shouldCreateChatModelFromCustomConfiguration() {
    // given
    var mockChatModel = mock(ChatModel.class);
    var provider = createMockProvider("my-custom-provider");
    var customConfig = new CustomProviderConfiguration("my-custom-provider", Map.of("param1", "value1"));
    
    when(provider.createChatModel(customConfig)).thenReturn(mockChatModel);

    registry.registerProvider(provider);

    // when
    var result = registry.createChatModel(customConfig);

    // then
    assertThat(result).isSameAs(mockChatModel);
  }

  @Test
  void shouldFailToCreateChatModelWhenNoProviderFound() {
    // given
    var config = mock(AnthropicProviderConfiguration.class);

    // when/then
    assertThatThrownBy(() -> registry.createChatModel(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No chat model provider found for configuration type");
  }

  @Test
  void shouldFailToCreateChatModelWhenCustomProviderNotRegistered() {
    // given
    var customConfig = new CustomProviderConfiguration("unknown-provider", Map.of());

    // when/then
    assertThatThrownBy(() -> registry.createChatModel(customConfig))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No chat model provider registered for custom provider type: unknown-provider");
  }

  @Test
  void shouldRegisterMultipleProvidersFromList() {
    // given
    var provider1 = createMockProvider("provider1");
    var provider2 = createMockProvider("provider2");
    var providers = List.of(provider1, provider2);

    // when
    var newRegistry = new ChatModelProviderRegistry(providers);

    // then - should not throw
    var mockChatModel1 = mock(ChatModel.class);
    var mockChatModel2 = mock(ChatModel.class);
    var config1 = new CustomProviderConfiguration("provider1", Map.of());
    var config2 = new CustomProviderConfiguration("provider2", Map.of());

    when(provider1.createChatModel(config1)).thenReturn(mockChatModel1);
    when(provider2.createChatModel(config2)).thenReturn(mockChatModel2);

    assertThat(newRegistry.createChatModel(config1)).isSameAs(mockChatModel1);
    assertThat(newRegistry.createChatModel(config2)).isSameAs(mockChatModel2);
  }

  private ChatModelProvider createMockProvider(String type) {
    var provider = mock(ChatModelProvider.class);
    when(provider.getProviderType()).thenReturn(type);
    return provider;
  }
}
