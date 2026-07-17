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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.anthropic.AnthropicModelCapabilitiesData;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));

    assertThat(config.configuration()).isInstanceOf(AnthropicChatModel.class);
    assertThat(config.configuration().capabilityOverride()).isNull();
  }

  @Test
  void directAnthropicV2ConfigResolvesToAnthropicChatModelApi() {
    // Both the LangChain4J factory and the Anthropic factory are registered; they are disjoint by
    // configuration type (ProviderChatModelApiConfiguration vs.
    // LlmProviderChatModelApiConfiguration
    // with a direct backend), so only the Anthropic factory supports this configuration.
    final var registry =
        new ChatModelApiRegistryImpl(List.of(langchain4JFactory(), anthropicFactory()));

    final ChatModelApiConfiguration directConfig =
        new LlmProviderChatModelApiConfiguration(
            new AnthropicChatModel(
                new AnthropicConnection(
                    new AnthropicDirectBackend(null, "sk-ant"),
                    new AnthropicModel("claude-sonnet-4-6", null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));

    assertThat(registry.resolve(directConfig)).isInstanceOf(AnthropicChatModelApi.class);
  }

  @Test
  void bedrockAnthropicV2ConfigStillFailsLoud() {
    // Neither the Anthropic factory (direct-only) nor the LangChain4J factory
    // (ProviderChatModelApiConfiguration only) supports a bedrock-backed
    // LlmProviderChatModelApiConfiguration.
    final var registry =
        new ChatModelApiRegistryImpl(List.of(langchain4JFactory(), anthropicFactory()));

    final ChatModelApiConfiguration bedrockConfig =
        new LlmProviderChatModelApiConfiguration(
            new AnthropicChatModel(
                new AnthropicConnection(
                    new AnthropicBedrockBackend(
                        "eu-west-1", null, new AwsApiKeyAuthentication("api-key")),
                    new AnthropicModel("claude-sonnet-4-6", null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));

    assertThatThrownBy(() -> registry.resolve(bedrockConfig))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No chat model registered for configuration")
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
  }

  private static ChatModelApiFactory langchain4JFactory() {
    return new Langchain4JChatModelApiFactory(
        provider -> true,
        mock(ChatModelFactory.class),
        mock(ChatMessageConverter.class),
        mock(ToolSpecificationConverter.class),
        mock(JsonSchemaConverter.class),
        Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
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
