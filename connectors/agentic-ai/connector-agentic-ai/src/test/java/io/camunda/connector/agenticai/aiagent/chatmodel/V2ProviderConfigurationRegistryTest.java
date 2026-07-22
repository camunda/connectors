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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicModelCapabilitiesData;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.ChatModelAwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class V2ProviderConfigurationRegistryTest {

  @Test
  void anthropicChatModelExposesCapabilityOverrideDirectly() {
    final ChatModelApiConfiguration config =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant"),
                null,
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(config).isInstanceOf(AnthropicChatModel.class);
    assertThat(((AnthropicChatModel) config).capabilityOverride()).isNull();
  }

  @Test
  void directAnthropicV2ConfigResolvesToAnthropicChatModelApi() {
    // Both the LangChain4J factory and the Anthropic factory are registered; they are disjoint by
    // configuration type (V1ProviderConfiguration vs. V2ProviderConfiguration with a direct
    // backend), so only the Anthropic factory supports this configuration.
    final var registry =
        new ChatModelApiRegistryImpl(List.of(langchain4JFactory(), anthropicFactory()));

    final ChatModelApiConfiguration directConfig =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant"),
                null,
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(registry.resolve(directConfig)).isInstanceOf(AnthropicChatModelApi.class);
  }

  @Test
  void bedrockAnthropicV2ConfigStillFailsLoud() {
    // Neither the Anthropic factory (direct-only) nor the LangChain4J factory
    // (V1ProviderConfiguration only) supports a bedrock-backed V2ProviderConfiguration.
    final var registry =
        new ChatModelApiRegistryImpl(List.of(langchain4JFactory(), anthropicFactory()));

    final ChatModelApiConfiguration bedrockConfig =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicBedrockBackend(
                    "eu-west-1", null, new AwsApiKeyAuthentication("api-key")),
                null,
                null,
                new AnthropicModel("claude-sonnet-4-6", null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThatThrownBy(() -> registry.resolve(bedrockConfig))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No chat model registered for configuration")
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  private static ChatModelApiFactory langchain4JFactory() {
    // stands in for a registered Langchain4JChatModelApiFactory subclass, which only ever
    // supports V1ProviderConfiguration and therefore never matches the V2 configurations used
    // in this test
    return mock(ChatModelApiFactory.class);
  }

  private static ChatModelApiFactory anthropicFactory() {
    final var transport = mock(HttpTransportSupport.class);
    when(transport.okHttpProxy(ArgumentMatchers.any())).thenReturn(Optional.empty());
    final var capabilitiesResolver = mock(ModelCapabilitiesResolver.class);
    when(capabilitiesResolver.resolve(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(AnthropicModelCapabilitiesData.class)))
        .thenReturn(
            new AnthropicModelCapabilities(
                new CoreModelCapabilities(List.of(), List.of(), List.of(), null, null), null));
    return new AnthropicChatModelApiFactory(transport, capabilitiesResolver, new ObjectMapper());
  }
}
