/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiChatModelApiFactoryTest {

  private static final String MODEL_ID = "gpt-5.4";

  @Mock private HttpTransportSupport transport;
  @Mock private ModelCapabilitiesResolver capabilitiesResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OpenAiChatModelApiFactory factory;

  @BeforeEach
  void setUp() {
    factory = new OpenAiChatModelApiFactory(transport, capabilitiesResolver, objectMapper);
  }

  @Test
  void supportsOpenAiDirectAndCompatible() {
    assertThat(factory.supports(directConfig(OpenAiApiFamily.RESPONSES, MODEL_ID))).isTrue();
    assertThat(factory.supports(compatibleConfig(OpenAiApiFamily.COMPLETIONS, MODEL_ID))).isTrue();
  }

  @Test
  void doesNotSupportAnthropic() {
    final ChatModelApiConfiguration config =
        new AnthropicChatModel(
            new AnthropicConnection(
                new AnthropicDirectBackend(null, "sk-ant"),
                null,
                null,
                new AnthropicModel(MODEL_ID, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void doesNotSupportV1ProviderConfiguration() {
    final ChatModelApiConfiguration config =
        new AnthropicProviderConfiguration(
            new AnthropicProviderConfiguration.AnthropicConnection(
                null,
                new AnthropicAuthentication("api-key"),
                null,
                new AnthropicProviderConfiguration.AnthropicModel(MODEL_ID, null)));

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void createReturnsOpenAiChatModelApiForResponsesFamily() {
    final var capabilities = openAiCaps();
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());
    when(capabilitiesResolver.resolve(
            eq("openai-responses"),
            eq(MODEL_ID),
            eq("direct"),
            any(),
            eq(OpenAiModelCapabilitiesData.class)))
        .thenReturn(capabilities);
    when(capabilitiesResolver.matches("openai-responses", MODEL_ID, "direct")).thenReturn(true);

    final ChatModelApi api = factory.create(directConfig(OpenAiApiFamily.RESPONSES, MODEL_ID));

    assertThat(api).isInstanceOf(OpenAiChatModelApi.class);
    assertThat(api.capabilities()).isEqualTo(capabilities);
  }

  @Test
  void createReturnsOpenAiChatModelApiForCompletionsFamily() {
    final var capabilities = openAiCaps();
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());
    when(capabilitiesResolver.resolve(
            eq("openai-completions"),
            eq(MODEL_ID),
            eq("direct"),
            any(),
            eq(OpenAiModelCapabilitiesData.class)))
        .thenReturn(capabilities);
    when(capabilitiesResolver.matches("openai-completions", MODEL_ID, "direct")).thenReturn(true);

    final ChatModelApi api = factory.create(directConfig(OpenAiApiFamily.COMPLETIONS, MODEL_ID));

    assertThat(api).isInstanceOf(OpenAiChatModelApi.class);
    assertThat(api.capabilities()).isEqualTo(capabilities);
  }

  private static OpenAiModelCapabilities openAiCaps() {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }

  private static OpenAiChatModel directConfig(OpenAiApiFamily apiFamily, String modelId) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            apiFamily,
            new OpenAiDirectBackend("sk-openai", null, null),
            null,
            null,
            new OpenAiModel(modelId, null),
            null,
            null,
            null));
  }

  private static OpenAiChatModel compatibleConfig(OpenAiApiFamily apiFamily, String modelId) {
    return new OpenAiChatModel(
        new OpenAiConnection(
            apiFamily,
            new OpenAiCompatibleBackend(
                "https://example.com/v1",
                null,
                null,
                null,
                new CompatibleApiKeyAuthentication("api-key")),
            null,
            null,
            new OpenAiModel(modelId, null),
            null,
            null,
            null));
  }
}
