/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockChatModelConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.AwsAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.auth.scheme.BedrockRuntimeAuthSchemeProvider;

/**
 * Mirrors {@code BedrockChatModelFactoryTest} (the v1 langchain4j equivalent): a real, non-mocked
 * {@link AgenticAiHttpProxySupport} (backed by {@link ProxyConfiguration#NONE}) builds a real Netty
 * HTTP client builder, and only the top-level {@link BedrockRuntimeAsyncClient#builder()} factory
 * method is static-mocked so the spy can assert exactly which builder methods were called, while
 * still letting {@code build()} run for real via {@link ResultCaptor}.
 */
@ExtendWith(MockitoExtension.class)
class BedrockChatModelFactoryTest {

  private static final String MODEL_ID = "us.amazon.nova-2-lite-v1:0";
  private static final String REGION = "eu-central-1";
  private static final Region AWS_REGION = Region.of(REGION);

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final AgenticAiHttpProxySupport proxySupport =
      spy(new AgenticAiHttpProxySupport(proxyConfiguration));

  private final ChatModelProperties config =
      new ChatModelProperties(new ApiProperties(Duration.ofMinutes(3)));

  private final BedrockConverseContentConverter contentConverter =
      new BedrockConverseContentConverter(new ObjectMapper());
  private final BedrockConverseRequestConverter requestConverter =
      new BedrockConverseRequestConverter(contentConverter, new ObjectMapper());
  private final BedrockConverseResponseConverter responseConverter =
      new BedrockConverseResponseConverter();

  private final BedrockChatModelFactory factory =
      new BedrockChatModelFactory(
          config, proxySupport, requestConverter, responseConverter, new ObjectMapper());

  @Captor private ArgumentCaptor<AwsCredentialsProvider> credentialsProviderCaptor;

  @Test
  void supportsBedrockV2Config() {
    assertThat(factory.supports(bedrockConfig(defaultCredentialsAuth(), null))).isTrue();
  }

  @Test
  void doesNotSupportOtherProviderConfiguration() {
    final ChatModelConfiguration otherConfig =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(
                new AnthropicApiBackend(
                    new AnthropicApiBackend.AnthropicApi("sk-ant-test", null, null, null, null)),
                new AnthropicModel(MODEL_ID, null),
                null));

    assertThat(factory.supports(otherConfig)).isFalse();
  }

  @Test
  void createBuildsChatModelWrappingConstructedClient() {
    final var clientBuilder = spy(BedrockRuntimeAsyncClient.builder());
    doAnswer(new ResultCaptor<>()).when(clientBuilder).build();

    try (MockedStatic<BedrockRuntimeAsyncClient> clientMock =
        mockStatic(BedrockRuntimeAsyncClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(BedrockRuntimeAsyncClient::builder).thenReturn(clientBuilder);

      final var bedrockConfig = bedrockConfig(defaultCredentialsAuth(), null);
      final ChatModel chatModel = factory.create(bedrockConfig);
      try {
        assertThat(chatModel).isInstanceOf(BedrockChatModel.class);
      } finally {
        chatModel.close();
      }
    }
  }

  @Test
  void buildsClientWithStaticCredentials() {
    final var authentication =
        new AwsAuthentication.AwsStaticCredentialsAuthentication("AKIA", "secret");

    testBuilder(
        bedrockConfig(authentication, null),
        (clientBuilder) -> {
          verify(clientBuilder).region(AWS_REGION);
          verify(clientBuilder, never()).endpointOverride(any());
          verify(clientBuilder).credentialsProvider(credentialsProviderCaptor.capture());

          final var credentialsProvider = credentialsProviderCaptor.getValue();
          assertThat(credentialsProvider).isInstanceOf(StaticCredentialsProvider.class);
          final var credentials = credentialsProvider.resolveCredentials();
          assertThat(credentials.accessKeyId()).isEqualTo("AKIA");
          assertThat(credentials.secretAccessKey()).isEqualTo("secret");
        });
  }

  @Test
  void buildsClientWithDefaultCredentialsChain() {
    testBuilder(
        bedrockConfig(defaultCredentialsAuth(), null),
        (clientBuilder) -> {
          verify(clientBuilder).credentialsProvider(credentialsProviderCaptor.capture());
          assertThat(credentialsProviderCaptor.getValue())
              .isInstanceOf(DefaultCredentialsProvider.class);
        });
  }

  @Test
  void buildsClientWithApiKeyBearerAuthentication() {
    final var authentication = new AwsAuthentication.AwsApiKeyAuthentication("bedrock-key");

    testBuilder(
        bedrockConfig(authentication, null),
        (clientBuilder) -> {
          verify(clientBuilder, never()).credentialsProvider(any());

          final var tokenProviderCaptor = ArgumentCaptor.forClass(SdkTokenProvider.class);
          verify(clientBuilder).tokenProvider(tokenProviderCaptor.capture());
          assertThat(tokenProviderCaptor.getValue().resolveToken().token())
              .isEqualTo("bedrock-key");

          verify(clientBuilder).authSchemeProvider(any(BedrockRuntimeAuthSchemeProvider.class));

          final var overrideConfigurationCaptor =
              ArgumentCaptor.forClass(ClientOverrideConfiguration.class);
          verify(clientBuilder).overrideConfiguration(overrideConfigurationCaptor.capture());
          assertThat(overrideConfigurationCaptor.getValue().apiCallTimeout())
              .contains(Duration.ofMinutes(3));
        });
  }

  @Test
  void setsEndpointOverrideWhenEndpointPresent() {
    final var endpoint = "https://custom.example.com";

    testBuilder(
        bedrockConfig(defaultCredentialsAuth(), endpoint),
        (clientBuilder) -> {
          verify(clientBuilder).endpointOverride(URI.create(endpoint));
          verify(proxySupport).createAwsAsyncHttpClientBuilder(URI.create(endpoint));
        });
  }

  @Test
  void doesNotSetEndpointOverrideWhenEndpointAbsent() {
    testBuilder(
        bedrockConfig(defaultCredentialsAuth(), null),
        (clientBuilder) -> {
          verify(clientBuilder, never()).endpointOverride(any());
          verify(proxySupport).createAwsAsyncHttpClientBuilder(null);
        });
  }

  @ParameterizedTest
  @MethodSource("timeoutConfigurations")
  void appliesDerivedTimeoutToApiCallTimeout(
      TimeoutConfiguration timeouts, Duration expectedTimeout) {
    final var connection =
        new BedrockConnection(
            REGION, null, defaultCredentialsAuth(), null, null, null, timeouts, model());

    testBuilder(
        new BedrockChatModelConfiguration(connection),
        (clientBuilder) -> {
          final var overrideConfigurationCaptor =
              ArgumentCaptor.forClass(ClientOverrideConfiguration.class);
          verify(clientBuilder).overrideConfiguration(overrideConfigurationCaptor.capture());
          assertThat(overrideConfigurationCaptor.getValue().apiCallTimeout())
              .contains(expectedTimeout);
        });
  }

  static Stream<Arguments> timeoutConfigurations() {
    return Stream.of(
        Arguments.of(new TimeoutConfiguration(Duration.ofSeconds(45)), Duration.ofSeconds(45)),
        Arguments.of(null, Duration.ofMinutes(3)),
        Arguments.of(new TimeoutConfiguration(null), Duration.ofMinutes(3)));
  }

  private void testBuilder(
      BedrockChatModelConfiguration bedrockConfig,
      ThrowingBuilderConsumer<BedrockRuntimeAsyncClientBuilder> assertions) {
    final var clientBuilder = spy(BedrockRuntimeAsyncClient.builder());
    doAnswer(new ResultCaptor<>()).when(clientBuilder).build();

    try (MockedStatic<BedrockRuntimeAsyncClient> clientMock =
        mockStatic(BedrockRuntimeAsyncClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(BedrockRuntimeAsyncClient::builder).thenReturn(clientBuilder);

      try (var client = factory.buildAsyncClient(bedrockConfig.bedrock())) {
        assertThat(client).isNotNull();
        assertions.accept(clientBuilder);
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static BedrockChatModelConfiguration bedrockConfig(
      AwsAuthentication authentication, String endpoint) {
    return new BedrockChatModelConfiguration(
        new BedrockConnection(REGION, endpoint, authentication, null, null, null, null, model()));
  }

  private static AwsAuthentication.AwsDefaultCredentialsChainAuthentication
      defaultCredentialsAuth() {
    return new AwsAuthentication.AwsDefaultCredentialsChainAuthentication();
  }

  private static BedrockModel model() {
    return new BedrockModel(MODEL_ID, null);
  }

  @FunctionalInterface
  private interface ThrowingBuilderConsumer<T> {
    void accept(T value) throws Exception;
  }

  private static final class ResultCaptor<T> implements Answer<T> {
    @Override
    @SuppressWarnings("unchecked")
    public T answer(InvocationOnMock invocation) throws Throwable {
      return (T) invocation.callRealMethod();
    }
  }
}
