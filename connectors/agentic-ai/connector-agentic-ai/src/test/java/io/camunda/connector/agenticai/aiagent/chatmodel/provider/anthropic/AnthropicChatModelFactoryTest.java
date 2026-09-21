/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicCustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelFactoryTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;
  @Mock private OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ChatModelProperties chatModelProperties =
      new ChatModelProperties(
          new ApiProperties(Duration.ofMinutes(3)),
          new AzureProperties(new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  private AnthropicChatModelFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new AnthropicChatModelFactory(
            chatModelProperties,
            httpProxySupport,
            new AnthropicMessageRequestConverter(new AnthropicContentConverter(objectMapper)),
            new AnthropicMessageResponseConverter(objectMapper),
            oAuthClientCredentialsTokenResolver);
  }

  @ParameterizedTest
  @MethodSource("configs")
  void supportsAnthropicV2Config(AnthropicChatModelConfiguration config) {
    assertThat(factory.supports(config)).isTrue();
  }

  @Test
  void doesNotSupportOtherProviderConfiguration() {
    final ChatModelConfiguration config =
        new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of());

    assertThat(factory.supports(config)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("configs")
  void createBuildsWorkingApi(AnthropicChatModelConfiguration config) {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api = factory.create(config);

    assertThat(api).isNotNull().isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  static Stream<AnthropicChatModelConfiguration> configs() {
    return Stream.of(
        apiConfig(MODEL_ID),
        customConfig(MODEL_ID),
        bedrockConfig(
            MODEL_ID, new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA", "secret")));
  }

  @Test
  void createBuildsWorkingApiForBedrockBackendWithApiKey() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(
            bedrockConfig(MODEL_ID, new AwsAuthentication.AwsApiKeyAuthentication("bedrock-key")));

    assertThat(api).isNotNull().isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  @Test
  void createBuildsWorkingApiForBedrockBackendWithDefaultCredentialsChain() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(
            bedrockConfig(
                MODEL_ID, new AwsAuthentication.AwsDefaultCredentialsChainAuthentication()));

    assertThat(api).isNotNull().isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  @ParameterizedTest
  @MethodSource("timeoutConfigurations")
  void appliesDerivedTimeoutToClient(TimeoutConfiguration timeouts, Duration expectedTimeout) {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final var clientBuilder = spy(AnthropicOkHttpClient.builder());
    try (MockedStatic<AnthropicOkHttpClient> clientMock =
        mockStatic(AnthropicOkHttpClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(AnthropicOkHttpClient::builder).thenReturn(clientBuilder);

      final ChatModel api = factory.create(apiConfig(MODEL_ID, timeouts));
      verify(clientBuilder).timeout(expectedTimeout);
      api.close();
    }
  }

  static Stream<Arguments> timeoutConfigurations() {
    return Stream.of(
        Arguments.of(new TimeoutConfiguration(Duration.ofSeconds(45)), Duration.ofSeconds(45)),
        Arguments.of(null, Duration.ofMinutes(3)),
        Arguments.of(new TimeoutConfiguration(null), Duration.ofMinutes(3)),
        Arguments.of(new TimeoutConfiguration(Duration.ZERO), Duration.ofMinutes(3)));
  }

  private static AnthropicChatModelConfiguration apiConfig(String modelId) {
    return apiConfig(modelId, null);
  }

  private static AnthropicChatModelConfiguration apiConfig(
      String modelId, TimeoutConfiguration timeouts) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicApiBackend(
                new AnthropicApiBackend.AnthropicApi("sk-ant-test", null, null, null, null)),
            new AnthropicModel(modelId, null),
            timeouts));
  }

  private static AnthropicChatModelConfiguration customConfig(String modelId) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicCustomBackend(
                new AnthropicCustomBackend.CustomBackend(
                    "https://custom.example.com", null, null, null, new NoAuthentication())),
            new AnthropicModel(modelId, null),
            null));
  }

  private static AnthropicChatModelConfiguration bedrockConfig(
      String modelId, AwsAuthentication authentication) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicAwsBedrockMantleBackend(
                new AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend(
                    "eu-central-1", null, authentication, null, null, null)),
            new AnthropicModel(modelId, null),
            null));
  }
}
