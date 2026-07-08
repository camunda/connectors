/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelApiRegistryImplTest {

  @Test
  void resolvesChatModelFromSupportingFactory() {
    final var chatModel = mock(ChatModelApi.class);
    final var factory = stubFactory(true, chatModel, ChatModelApiFactory.DEFAULT_ORDER);

    final var registry = new ChatModelApiRegistryImpl(List.of(factory));

    assertThat(registry.resolve(validProviderConfiguration())).isSameAs(chatModel);
  }

  @Test
  void higherPrecedenceFactoryOverridesLowerPrecedenceFactory() {
    final var lowPrecedenceChatModel = mock(ChatModelApi.class);
    final var lowPrecedenceFactory = stubFactory(true, lowPrecedenceChatModel, Integer.MAX_VALUE);

    final var highPrecedenceChatModel = mock(ChatModelApi.class);
    final var highPrecedenceFactory =
        stubFactory(true, highPrecedenceChatModel, ChatModelApiFactory.DEFAULT_ORDER);

    // register the low-precedence factory first to ensure ordering, not registration order, wins
    final var registry =
        new ChatModelApiRegistryImpl(List.of(lowPrecedenceFactory, highPrecedenceFactory));

    assertThat(registry.resolve(validProviderConfiguration())).isSameAs(highPrecedenceChatModel);
  }

  @Test
  void throwsWhenNoFactorySupportsConfiguration() {
    final var factory =
        stubFactory(false, mock(ChatModelApi.class), ChatModelApiFactory.DEFAULT_ORDER);
    final var registry = new ChatModelApiRegistryImpl(List.of(factory));

    assertThatThrownBy(() -> registry.resolve(validProviderConfiguration()))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e ->
                assertThat(((ConnectorException) e).getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL));
  }

  @Test
  void throwsWhenRegistryIsEmpty() {
    final var registry = new ChatModelApiRegistryImpl(List.of());

    assertThatThrownBy(() -> registry.resolve(validProviderConfiguration()))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e ->
                assertThat(((ConnectorException) e).getErrorCode())
                    .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL));
  }

  private static ChatModelApiFactory stubFactory(
      boolean supports, ChatModelApi chatModel, int order) {
    return new ChatModelApiFactory() {
      @Override
      public boolean supports(ChatModelApiConfiguration configuration) {
        return supports;
      }

      @Override
      public ChatModelApi create(ChatModelApiConfiguration configuration) {
        return chatModel;
      }

      @Override
      public int getOrder() {
        return order;
      }
    };
  }

  private static ChatModelApiConfiguration validProviderConfiguration() {
    return new ProviderChatModelApiConfiguration(
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("api-key"),
                null,
                new AnthropicModel("claude", null))));
  }
}
