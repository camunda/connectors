/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.shared.ChatModelAwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelApiFactoryTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  @Mock private HttpTransportSupport transport;
  @Mock private ModelCapabilitiesResolver capabilitiesResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private AnthropicChatModelApiFactory factory;

  @BeforeEach
  void setUp() {
    factory = new AnthropicChatModelApiFactory(transport, capabilitiesResolver, objectMapper);
  }

  @Test
  void supportsAnthropicDirectV2Config() {
    assertThat(factory.supports(directConfig(MODEL_ID))).isTrue();
  }

  @Test
  void doesNotSupportBedrockBackend() {
    final var config =
        new LlmProviderChatModelApiConfiguration(
            new AnthropicChatModel(
                new AnthropicConnection(
                    new AnthropicBedrockBackend(
                        "eu-west-1", null, new AwsApiKeyAuthentication("api-key")),
                    new AnthropicModel(MODEL_ID, null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void doesNotSupportBridgeConfig() {
    final ChatModelApiConfiguration config =
        new ProviderChatModelApiConfiguration(
            new AnthropicProviderConfiguration(
                new AnthropicProviderConfiguration.AnthropicConnection(
                    null,
                    new AnthropicAuthentication("api-key"),
                    null,
                    new AnthropicProviderConfiguration.AnthropicModel(MODEL_ID, null))));

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void createResolvesCapabilitiesWithAnthropicMessagesFamily() {
    final var capabilities = ModelCapabilities.builder().build();
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());
    when(capabilitiesResolver.resolve(eq("anthropic-messages"), eq(MODEL_ID), eq("direct"), any()))
        .thenReturn(capabilities);

    final ChatModelApi api = factory.create(directConfig(MODEL_ID));

    assertThat(api).isNotNull();
    assertThat(api.capabilities()).isEqualTo(capabilities);
    verify(capabilitiesResolver)
        .resolve("anthropic-messages", MODEL_ID, "direct", Optional.empty());
  }

  @Test
  void getOrderIsBelowBridge() {
    assertThat(factory.getOrder()).isEqualTo(100);
    assertThat(factory.getOrder()).isLessThan(1000);
  }

  private static LlmProviderChatModelApiConfiguration directConfig(String modelId) {
    return new LlmProviderChatModelApiConfiguration(
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant"),
                new AnthropicModel(modelId, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)));
  }
}
