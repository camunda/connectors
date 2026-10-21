/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.testsupport.ModelCallEnvelopeFixture.delegateOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.CredentialCacheSettings;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.CustomChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.proxy.azure.AzureHttpProxySupport;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
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
  @Mock private OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private AnthropicChatModelFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new AnthropicChatModelFactory(
            new ConnectionResolver(httpProxySupport, oAuthClientCredentialsTokenResolver),
            new FoundryCredentialResolver(
                new EntraIdTokenCredentialFactory(
                    new AzureHttpProxySupport(ProxyConfiguration.NONE),
                    new CredentialCacheSettings(true, 100L, Duration.ofMinutes(10)))),
            new AnthropicMessageRequestConverter(new AnthropicContentConverter(objectMapper)),
            new AnthropicMessageResponseConverter(objectMapper));
  }

  @ParameterizedTest
  @MethodSource("configs")
  void supportsAnthropicConfiguration(AnthropicConfiguration config) {
    assertThat(factory.supports(config)).isTrue();
  }

  @Test
  void doesNotSupportOtherProviderConfiguration() {
    final ChatModelConfiguration config =
        new CustomChatModelConfiguration("some-custom-provider", MODEL_ID, Map.of());

    assertThat(factory.supports(config)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("configs")
  void createBuildsWorkingApi(AnthropicConfiguration config) {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api = factory.create(config);

    assertThat(delegateOf(api)).isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  static Stream<AnthropicConfiguration> configs() {
    return Stream.of(
        apiConfig(MODEL_ID),
        customConfig(MODEL_ID),
        bedrockConfig(MODEL_ID, new AwsAuthentication.StaticCredentials("AKIA", "secret")),
        foundryConfig(MODEL_ID, new FoundryAuthentication.ApiKey("foundry-key")));
  }

  @Test
  void createBuildsWorkingApiForFoundryBackendWithClientCredentials() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(
            foundryConfig(
                MODEL_ID,
                new FoundryAuthentication.ClientCredentials(
                    "client-id", "client-secret", "tenant-id", null, null)));

    assertThat(delegateOf(api)).isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  @Test
  void createBuildsWorkingApiForFoundryBackendWithManagedIdentity() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(
            foundryConfig(MODEL_ID, new FoundryAuthentication.ManagedIdentity(null, null)));

    assertThat(delegateOf(api)).isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  @Test
  void createBuildsWorkingApiForBedrockBackendWithApiKey() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(bedrockConfig(MODEL_ID, new AwsAuthentication.ApiKey("bedrock-key")));

    assertThat(delegateOf(api)).isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  @Test
  void createBuildsWorkingApiForBedrockBackendWithDefaultCredentialsChain() {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api =
        factory.create(bedrockConfig(MODEL_ID, new AwsAuthentication.DefaultCredentialsChain()));

    assertThat(delegateOf(api)).isInstanceOf(AnthropicChatModel.class);
    api.close();
  }

  private static AnthropicConfiguration apiConfig(String modelId) {
    return configuration(new Backend.Api("sk-ant-test", null, RequestCustomizations.NONE), modelId);
  }

  private static AnthropicConfiguration customConfig(String modelId) {
    return configuration(
        new Backend.Custom(
            "https://custom.example.com",
            new EndpointAuthentication.None(),
            RequestCustomizations.NONE),
        modelId);
  }

  private static AnthropicConfiguration bedrockConfig(
      String modelId, AwsAuthentication authentication) {
    return configuration(
        new Backend.AwsBedrockMantle(
            "eu-central-1", null, authentication, RequestCustomizations.NONE),
        modelId);
  }

  private static AnthropicConfiguration foundryConfig(
      String modelId, FoundryAuthentication authentication) {
    return configuration(
        new Backend.Foundry(
            "https://your-resource.services.ai.azure.com",
            authentication,
            RequestCustomizations.NONE),
        modelId);
  }

  private static AnthropicConfiguration configuration(Backend backend, String modelId) {
    return new AnthropicConfiguration(
        backend,
        modelId,
        Duration.ofMinutes(3),
        null,
        null,
        false,
        AnthropicConfiguration.DEFAULT_MAX_TOKENS,
        null,
        null,
        null);
  }
}
