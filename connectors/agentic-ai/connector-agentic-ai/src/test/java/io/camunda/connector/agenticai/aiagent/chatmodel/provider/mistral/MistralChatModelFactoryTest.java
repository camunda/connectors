/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MISTRAL_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MistralChatModelFactoryTest {

  private static final String MODEL_ID = "mistral-medium-latest";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ChatModelProperties chatModelProperties =
      new ChatModelProperties(
          new ApiProperties(Duration.ofMinutes(3)),
          new AzureProperties(new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  private MistralChatModelFactory factory;

  @BeforeEach
  void setUp() {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    factory =
        new MistralChatModelFactory(
            chatModelProperties,
            httpProxySupport,
            new OpenAiCompletionsRequestConverter(
                contentConverter,
                OpenAiCompletionsContentChunkStrategy.mistral(objectMapper),
                objectMapper,
                MISTRAL_ID),
            new OpenAiCompletionsResponseConverter(MISTRAL_ID, objectMapper),
            OpenAiCompletionsStreamAssembler.chunkedContentAware());
  }

  private static MistralChatModelConfiguration config(
      String modelId, TimeoutConfiguration timeouts) {
    return new MistralChatModelConfiguration(
        new MistralConnection(
            new MistralApiBackend(
                new MistralApiConnection("sk-mistral-test", null, null, null, null)),
            new MistralModel(modelId),
            null,
            timeouts));
  }

  @Test
  void supportsOnlyMistralConfiguration() {
    assertThat(factory.supports(config(MODEL_ID, null))).isTrue();

    final ChatModelConfiguration otherProvider =
        new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of());
    assertThat(factory.supports(otherProvider)).isFalse();
  }

  @Test
  void createBuildsWorkingChatModel() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());

    try (ChatModel chatModel = factory.create(config(MODEL_ID, null))) {
      assertThat(chatModel).isInstanceOf(MistralChatModel.class);
    }
  }

  @Test
  void createAppliesConfiguredTimeout() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());

    final var clientBuilder = spy(OpenAIOkHttpClient.builder());
    try (MockedStatic<OpenAIOkHttpClient> clientMock =
        mockStatic(OpenAIOkHttpClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(OpenAIOkHttpClient::builder).thenReturn(clientBuilder);

      try (ChatModel chatModel =
          factory.create(config(MODEL_ID, new TimeoutConfiguration(Duration.ofSeconds(7))))) {
        verify(clientBuilder).timeout(Duration.ofSeconds(7));
      }
    }
  }

  @Test
  void fallsBackToMistralDefaultEndpointWhenNoneConfigured() {
    // endpoint() is null in config(...) below -- the built client's baseUrl must still resolve to
    // Mistral's own default rather than openai-java's built-in default (api.openai.com), which
    // would otherwise silently send the Mistral API key to OpenAI.
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());

    final var clientBuilder = spy(OpenAIOkHttpClient.builder());
    try (MockedStatic<OpenAIOkHttpClient> clientMock =
        mockStatic(OpenAIOkHttpClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(OpenAIOkHttpClient::builder).thenReturn(clientBuilder);

      try (ChatModel chatModel = factory.create(config(MODEL_ID, null))) {
        verify(clientBuilder).baseUrl("https://api.mistral.ai/v1");
      }
    }
  }
}
