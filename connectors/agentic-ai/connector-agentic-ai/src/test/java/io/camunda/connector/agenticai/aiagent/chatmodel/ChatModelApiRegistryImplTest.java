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

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelApiRegistryImplTest {

  @Test
  void resolvesChatModelFromSupportingFactory() {
    final var chatModel = mock(ChatModelApi.class);
    final var factory = stubFactory(true, chatModel);

    final var registry = new ChatModelApiRegistryImpl(List.of(factory));

    assertThat(registry.resolve(validProviderConfiguration())).isSameAs(chatModel);
  }

  @Test
  void throwsWhenNoFactorySupportsConfiguration() {
    final var factory = stubFactory(false, mock(ChatModelApi.class));
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

  @Test
  void throwsWhenMultipleFactoriesSupportConfiguration() {
    final var registry =
        new ChatModelApiRegistryImpl(
            List.of(new FirstMatchingFactory(), new SecondMatchingFactory()));

    assertThatThrownBy(() -> registry.resolve(validProviderConfiguration()))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
              assertThat(connectorException.getMessage())
                  .contains("Multiple chat model factories match configuration")
                  .contains("FirstMatchingFactory")
                  .contains("SecondMatchingFactory");
            });
  }

  private static ChatModelApiFactory stubFactory(boolean supports, ChatModelApi chatModel) {
    return new ChatModelApiFactory() {
      @Override
      public boolean supports(ChatModelApiConfiguration configuration) {
        return supports;
      }

      @Override
      public ChatModelApi create(ChatModelApiConfiguration configuration) {
        return chatModel;
      }
    };
  }

  private static ChatModelApiConfiguration validProviderConfiguration() {
    return new V1ChatModelApiConfiguration(
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication("api-key"),
                null,
                new AnthropicModel("claude", null))));
  }

  private static class FirstMatchingFactory implements ChatModelApiFactory {
    @Override
    public boolean supports(ChatModelApiConfiguration configuration) {
      return true;
    }

    @Override
    public ChatModelApi create(ChatModelApiConfiguration configuration) {
      return mock(ChatModelApi.class);
    }
  }

  private static class SecondMatchingFactory implements ChatModelApiFactory {
    @Override
    public boolean supports(ChatModelApiConfiguration configuration) {
      return true;
    }

    @Override
    public ChatModelApi create(ChatModelApiConfiguration configuration) {
      return mock(ChatModelApi.class);
    }
  }
}
