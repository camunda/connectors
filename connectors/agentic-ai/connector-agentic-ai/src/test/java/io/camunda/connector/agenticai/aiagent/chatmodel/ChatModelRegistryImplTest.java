/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelRegistryImplTest {

  private final ChatModelConfiguration configuration = mock(ChatModelConfiguration.class);

  @Test
  void resolvesTheSingleMatchingFactory() {
    final var matchingFactory = factory(true);
    final var otherFactory = factory(false);
    final var expectedChatModel = mock(ChatModel.class);
    when(matchingFactory.create(configuration)).thenReturn(expectedChatModel);

    final var registry = new ChatModelRegistryImpl(List.of(otherFactory, matchingFactory));

    assertThat(registry.resolve(configuration)).isSameAs(expectedChatModel);
  }

  @Test
  void throwsWhenNoFactorySupportsTheConfiguration() {
    final var registry = new ChatModelRegistryImpl(List.of(factory(false), factory(false)));

    assertThatThrownBy(() -> registry.resolve(configuration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No chat model registered for configuration");
  }

  @Test
  void throwsWhenMultipleFactoriesSupportTheConfiguration() {
    final var registry =
        new ChatModelRegistryImpl(List.of(factory(true), factory(true), factory(false)));

    assertThatThrownBy(() -> registry.resolve(configuration))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Multiple chat model factories match configuration");
  }

  private ChatModelFactory factory(boolean supports) {
    final ChatModelFactory factory = mock(ChatModelFactory.class);
    when(factory.supports(configuration)).thenReturn(supports);
    return factory;
  }
}
