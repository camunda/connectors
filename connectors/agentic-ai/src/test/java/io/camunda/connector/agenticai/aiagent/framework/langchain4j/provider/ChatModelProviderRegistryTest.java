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

import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelProviderRegistryTest {

  @Test
  void resolvesProviderByType() {
    final var anthropicProvider = providerFor(AnthropicProviderConfiguration.ANTHROPIC_ID);
    final var otherProvider = providerFor("other");

    final var registry = new ChatModelProviderRegistry(List.of(anthropicProvider, otherProvider));

    final ProviderConfiguration config = validAnthropicConfig();

    assertThat(registry.getChatModelProvider(config)).isSameAs(anthropicProvider);
  }

  @Test
  void resolvesProviderRegisteredViaRegisterMethod() {
    final var registry = new ChatModelProviderRegistry();
    final var provider = providerFor(AnthropicProviderConfiguration.ANTHROPIC_ID);
    registry.registerChatModelProvider(provider);

    assertThat(registry.getChatModelProvider(validAnthropicConfig())).isSameAs(provider);
  }

  @Test
  void throwsWhenNoProviderRegisteredForType() {
    final var registry = new ChatModelProviderRegistry();

    assertThatThrownBy(() -> registry.getChatModelProvider(validAnthropicConfig()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No chat model provider registered for provider type: "
                + AnthropicProviderConfiguration.ANTHROPIC_ID);
  }

  @Test
  void throwsWhenRegisteringDuplicateProviderViaConstructor() {
    assertThatThrownBy(
            () ->
                new ChatModelProviderRegistry(
                    List.of(providerFor("duplicate"), providerFor("duplicate"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chat model provider with type 'duplicate' is already registered.");
  }

  @Test
  void throwsWhenRegisteringDuplicateProviderViaRegisterMethod() {
    final var registry = new ChatModelProviderRegistry();
    registry.registerChatModelProvider(providerFor("duplicate"));

    assertThatThrownBy(() -> registry.registerChatModelProvider(providerFor("duplicate")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chat model provider with type 'duplicate' is already registered.");
  }

  @Test
  void throwsWhenRegisteringDuplicateProviderViaAdditionalRegisterMethod() {
    final var registry = new ChatModelProviderRegistry(List.of(providerFor("duplicate")));

    assertThatThrownBy(() -> registry.registerChatModelProvider(providerFor("duplicate")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chat model provider with type 'duplicate' is already registered.");
  }

  private static ChatModelProvider providerFor(String type) {
    final var provider = mock(ChatModelProvider.class);
    when(provider.type()).thenReturn(type);
    return provider;
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
