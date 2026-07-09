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
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JAiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmProviderChatModelApiConfigurationRegistryTest {

  @Test
  void wrapsProviderConfigAndExposesCapabilityOverrideViaConfiguration() {
    final var config =
        new LlmProviderChatModelApiConfiguration(
            new AnthropicChatModel(
                new AnthropicConnection(
                    new AnthropicDirectBackend(null, "sk-ant"),
                    new AnthropicModel("claude-sonnet-4-6", null),
                    null,
                    null)));

    assertThat(config.configuration()).isInstanceOf(AnthropicChatModel.class);
    assertThat(config.configuration().capabilityOverride()).isNull();
  }

  @Test
  void registryFailsLoudWhenNoFactorySupportsLlmProviderConfiguration() {
    // Only the bridge factory is registered; it supports ProviderChatModelApiConfiguration only.
    final ChatModelApiFactory bridge =
        new Langchain4JChatModelApiFactory(mock(Langchain4JAiFrameworkAdapter.class));
    final var registry = new ChatModelApiRegistryImpl(List.of(bridge));

    final ChatModelApiConfiguration llmProviderConfig =
        new LlmProviderChatModelApiConfiguration(
            new AnthropicChatModel(
                new AnthropicConnection(
                    new AnthropicDirectBackend(null, "sk-ant"),
                    new AnthropicModel("claude-sonnet-4-6", null),
                    null,
                    null)));

    assertThatThrownBy(() -> registry.resolve(llmProviderConfig))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No chat model registered for configuration")
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }
}
