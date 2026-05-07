/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelApiRegistryImplTest {

  @Test
  void resolvesFactoryByProviderType() {
    final var anthropicFactory = factoryFor(AnthropicProviderConfiguration.ANTHROPIC_ID);
    final var resolvedApi = mock(ChatModelApi.class);
    when(anthropicFactory.create(any())).thenReturn(resolvedApi);

    final var otherFactory = factoryFor("other");

    final var registry = new ChatModelApiRegistryImpl(List.of(anthropicFactory, otherFactory));

    assertThat(registry.resolve(validAnthropicConfig())).isSameAs(resolvedApi);
  }

  @Test
  void throwsWhenNoFactoryRegisteredForType() {
    final var registry = new ChatModelApiRegistryImpl(List.of());

    assertThatThrownBy(() -> registry.resolve(validAnthropicConfig()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No chat model API factory registered for provider type '%s'"
                .formatted(AnthropicProviderConfiguration.ANTHROPIC_ID));
  }

  @Test
  void throwsWhenTwoFactoriesClaimSameProviderType() {
    final var first = factoryFor("duplicate");
    final var second = factoryFor("duplicate");

    assertThatThrownBy(() -> new ChatModelApiRegistryImpl(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Two chat model API factories claim provider type 'duplicate'");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ChatModelApiFactory<ProviderConfiguration> factoryFor(String providerType) {
    final ChatModelApiFactory<ProviderConfiguration> factory = mock(ChatModelApiFactory.class);
    when(factory.providerType()).thenReturn(providerType);
    when(factory.apiFamily()).thenReturn("test");
    when(factory.configurationType()).thenReturn((Class) ProviderConfiguration.class);
    return factory;
  }

  private static AnthropicProviderConfiguration validAnthropicConfig() {
    return new AnthropicProviderConfiguration(
        new AnthropicConnection(
            null,
            new AnthropicAuthentication("api-key"),
            null,
            new AnthropicModel("claude", null)));
  }
}
