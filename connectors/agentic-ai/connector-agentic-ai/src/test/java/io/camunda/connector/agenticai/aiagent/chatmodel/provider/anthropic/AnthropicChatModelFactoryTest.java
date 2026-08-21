/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicCustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelFactoryTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private AnthropicChatModelFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new AnthropicChatModelFactory(
            httpProxySupport,
            new AnthropicMessageRequestConverter(new AnthropicContentConverter(objectMapper)),
            new AnthropicMessageResponseConverter(objectMapper));
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

  private static AnthropicChatModelConfiguration apiConfig(String modelId) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicApiBackend(
                new AnthropicApiBackend.AnthropicApi("sk-ant-test", null, null, null, null)),
            new AnthropicModel(modelId, null),
            null));
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
