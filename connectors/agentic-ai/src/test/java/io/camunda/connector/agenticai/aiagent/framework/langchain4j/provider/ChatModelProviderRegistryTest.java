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

import io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatModelProviderRegistryTest {

  @Test
  void resolvesProviderByType() {
    final var customProvider = providerFor("my-custom");
    final var otherProvider = providerFor("other");

    final var registry = new ChatModelProviderRegistry(List.of(customProvider, otherProvider));

    final var config = new CustomProviderConfiguration("my-custom", Map.of("key", "value"));

    assertThat(registry.getChatModelProvider(config)).isSameAs(customProvider);
  }

  @Test
  void resolvesProviderRegisteredViaRegisterMethod() {
    final var registry = new ChatModelProviderRegistry();
    final var provider = providerFor("my-custom");
    registry.registerChatModelProvider(provider);

    assertThat(registry.getChatModelProvider(new CustomProviderConfiguration("my-custom", null)))
        .isSameAs(provider);
  }

  @Test
  void throwsWhenNoProviderRegisteredForType() {
    final var registry = new ChatModelProviderRegistry();
    final var config = new CustomProviderConfiguration("unknown", null);

    assertThatThrownBy(() -> registry.getChatModelProvider(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No chat model provider registered for provider type: unknown");
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
}
