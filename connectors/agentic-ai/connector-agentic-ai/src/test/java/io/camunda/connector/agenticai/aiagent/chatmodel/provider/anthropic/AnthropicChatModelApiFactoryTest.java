/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.V1ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicBedrockBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.ChatModelAwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.List;
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
        new V2ChatModelApiConfiguration(
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
                    null,
                    null,
                    null)));

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void doesNotSupportV1ChatModelApiConfiguration() {
    final ChatModelApiConfiguration config =
        new V1ChatModelApiConfiguration(
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
    final var capabilities = anthropicCaps();
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());
    when(capabilitiesResolver.resolve(
            eq("anthropic-messages"),
            eq(MODEL_ID),
            eq("direct"),
            any(),
            eq(AnthropicModelCapabilitiesData.class)))
        .thenReturn(capabilities);
    when(capabilitiesResolver.matches("anthropic-messages", MODEL_ID, "direct")).thenReturn(true);

    final ChatModelApi api = factory.create(directConfig(MODEL_ID));

    assertThat(api).isNotNull();
    assertThat(api.capabilities()).isEqualTo(capabilities);
    verify(capabilitiesResolver)
        .resolve(
            "anthropic-messages",
            MODEL_ID,
            "direct",
            Optional.empty(),
            AnthropicModelCapabilitiesData.class);
    verify(capabilitiesResolver).matches("anthropic-messages", MODEL_ID, "direct");
  }

  @Test
  void createQueriesMatchesSignalIndependentlyOfResolvedResult() {
    // matches() reports whether the model matched a matrix entry, distinct from resolve()'s
    // returned capabilities (which fall back to family/conservative defaults on a miss); the
    // factory must query it for every create() call so the reasoning validator downstream can
    // distinguish "declared but not reasoning-capable" from "unknown/custom model".
    final var capabilities = anthropicCaps();
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());
    when(capabilitiesResolver.resolve(
            eq("anthropic-messages"),
            eq(MODEL_ID),
            eq("direct"),
            any(),
            eq(AnthropicModelCapabilitiesData.class)))
        .thenReturn(capabilities);
    when(capabilitiesResolver.matches("anthropic-messages", MODEL_ID, "direct")).thenReturn(false);

    final ChatModelApi api = factory.create(directConfig(MODEL_ID));

    assertThat(api).isNotNull();
    verify(capabilitiesResolver).matches("anthropic-messages", MODEL_ID, "direct");
  }

  private static AnthropicModelCapabilities anthropicCaps() {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }

  private static V2ChatModelApiConfiguration directConfig(String modelId) {
    return new V2ChatModelApiConfiguration(
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
                null,
                null,
                null)));
  }
}
