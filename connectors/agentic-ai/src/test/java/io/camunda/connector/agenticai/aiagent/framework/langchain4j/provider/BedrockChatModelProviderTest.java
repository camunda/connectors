/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.MODEL_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.createDefaultConfigurationProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockModel.BedrockModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

@ExtendWith(MockitoExtension.class)
class BedrockChatModelProviderTest {

  private static final String BEDROCK_REGION = "eu-west-1";
  private static final String BEDROCK_API_KEY = "bedrockApiKey";
  private static final String BEDROCK_ACCESS_KEY = "bedrockAccessKey";
  private static final String BEDROCK_SECRET_KEY = "bedrockSecretKey";
  private static final String BEDROCK_MODEL = "bedrockModel";

  private static final BedrockModelParameters DEFAULT_MODEL_PARAMETERS =
      new BedrockModelParameters(10, 1.0, 0.8);

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration)));

  private final BedrockChatModelProvider provider =
      new BedrockChatModelProvider(createDefaultConfigurationProperties(), proxySupport);

  @Captor private ArgumentCaptor<ChatRequestParameters> modelParametersArgumentCaptor;
  @Captor private ArgumentCaptor<AwsCredentialsProvider> credentialsProviderArgumentCaptor;

  @Test
  void createsBedrockChatModelWithDefaultCredentials() {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                null,
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testCreateBedrockChatModelWithCredentials(
        providerConfig,
        (credentialsProvider) ->
            assertThat(credentialsProvider)
                .isNotNull()
                .isInstanceOf(DefaultCredentialsProvider.class));
  }

  @Test
  void createsBedrockChatModelWithBasicCredentials() {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                null,
                new AwsAuthentication.AwsStaticCredentialsAuthentication(
                    BEDROCK_ACCESS_KEY, BEDROCK_SECRET_KEY),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testCreateBedrockChatModelWithCredentials(
        providerConfig,
        (credentialsProvider) -> {
          assertThat(credentialsProvider).isNotNull().isInstanceOf(StaticCredentialsProvider.class);

          final var credentials = credentialsProvider.resolveCredentials();
          assertThat(credentials).isNotNull().isInstanceOf(AwsBasicCredentials.class);
          assertThat(credentials.accessKeyId()).isEqualTo(BEDROCK_ACCESS_KEY);
          assertThat(credentials.secretAccessKey()).isEqualTo(BEDROCK_SECRET_KEY);
        });
  }

  @Test
  void createsBedrockChatModelWithApiKeyCredentials() {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                null,
                new AwsAuthentication.AwsApiKeyAuthentication(BEDROCK_API_KEY),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testBedrockChatModelBuilder(
        providerConfig,
        (builders) -> {
          verify(builders.chatModelBuilder).timeout(MODEL_TIMEOUT.timeout());

          var clientBuilder = builders.clientBuilder;

          var overrideConfigurationCaptor =
              ArgumentCaptor.forClass(ClientOverrideConfiguration.class);
          verify(clientBuilder, atLeastOnce())
              .overrideConfiguration(overrideConfigurationCaptor.capture());

          assertThat(overrideConfigurationCaptor.getValue().headers())
              .containsEntry("Authorization", List.of("Bearer " + BEDROCK_API_KEY));
          assertThat(overrideConfigurationCaptor.getValue().apiCallTimeout())
              .isPresent()
              .contains(MODEL_TIMEOUT.timeout());
        });
  }

  @Test
  void createsBedrockChatModelWithCustomHttpsEndpoint() {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                "https://my-custom-endpoint.local",
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testBedrockChatModelBuilder(
        providerConfig,
        URI.create("https://my-custom-endpoint.local"),
        (builders) -> {
          verify(builders.clientBuilder)
              .endpointOverride(URI.create("https://my-custom-endpoint.local"));
        });
  }

  @Test
  void createsBedrockChatModelWithCustomHttpEndpoint() {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                "http://localhost:8080",
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testBedrockChatModelBuilder(
        providerConfig,
        URI.create("http://localhost:8080"),
        (builders) -> {
          verify(builders.clientBuilder).endpointOverride(URI.create("http://localhost:8080"));
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsBedrockChatModelWithNullModelParameters(BedrockModelParameters modelParameters) {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                null,
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                MODEL_TIMEOUT,
                new BedrockModel(BEDROCK_MODEL, modelParameters)));

    testBedrockChatModelBuilder(
        providerConfig,
        (builders) -> {
          if (modelParameters == null) {
            verify(builders.chatModelBuilder, never()).defaultRequestParameters(any());
          } else {
            verify(builders.chatModelBuilder)
                .defaultRequestParameters(modelParametersArgumentCaptor.capture());

            final var parameters = modelParametersArgumentCaptor.getValue();
            assertThat(parameters).isNotNull().isInstanceOf(BedrockChatRequestParameters.class);
            assertThat(parameters.maxOutputTokens()).isNull();
            assertThat(parameters.temperature()).isNull();
            assertThat(parameters.topP()).isNull();
          }
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsBedrockChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig =
        new BedrockProviderConfiguration(
            new BedrockConnection(
                BEDROCK_REGION,
                null,
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                timeouts,
                new BedrockModel(BEDROCK_MODEL, null)));

    testBedrockChatModelBuilder(
        providerConfig,
        (builder) -> verify(builder.chatModelBuilder).timeout(Duration.ofMinutes(3)));
  }

  private void testCreateBedrockChatModelWithCredentials(
      BedrockProviderConfiguration providerConfig,
      ThrowingConsumer<AwsCredentialsProvider> credentialsProviderAssertions) {
    testBedrockChatModelBuilder(
        providerConfig,
        (builders) -> {
          verify(builders.clientBuilder).region(Region.EU_WEST_1);
          verify(builders.clientBuilder, never()).endpointOverride(any());

          verify(builders.clientBuilder)
              .credentialsProvider(credentialsProviderArgumentCaptor.capture());
          credentialsProviderAssertions.accept(credentialsProviderArgumentCaptor.getValue());

          verify(builders.chatModelBuilder).client(builders.clientResultCaptor.getResult());
          verify(builders.chatModelBuilder).modelId(BEDROCK_MODEL);

          verify(builders.chatModelBuilder)
              .defaultRequestParameters(modelParametersArgumentCaptor.capture());

          final var parameters = modelParametersArgumentCaptor.getValue();
          assertThat(parameters).isNotNull().isInstanceOf(BedrockChatRequestParameters.class);
          assertThat(parameters.maxOutputTokens()).isEqualTo(DEFAULT_MODEL_PARAMETERS.maxTokens());
          assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
          assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
        });
  }

  private void testBedrockChatModelBuilder(
      BedrockProviderConfiguration providerConfig,
      ThrowingConsumer<BedrockBuilderContext> builderAssertions) {
    testBedrockChatModelBuilder(providerConfig, (URI) null, builderAssertions);
  }

  private void testBedrockChatModelBuilder(
      BedrockProviderConfiguration providerConfig,
      URI expectedEndpointOverride,
      ThrowingConsumer<BedrockBuilderContext> builderAssertions) {
    final var clientBuilder = spy(BedrockRuntimeClient.builder());
    final var clientResultCaptor = new ResultCaptor<BedrockRuntimeClient>();
    doAnswer(clientResultCaptor).when(clientBuilder).build();

    final var chatModelBuilder = spy(BedrockChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<BedrockRuntimeClient> clientMock =
            mockStatic(BedrockRuntimeClient.class, Answers.CALLS_REAL_METHODS);
        MockedStatic<BedrockChatModel> chatModelMock =
            mockStatic(BedrockChatModel.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(BedrockRuntimeClient::builder).thenReturn(clientBuilder);
      chatModelMock.when(BedrockChatModel::builder).thenReturn(chatModelBuilder);

      final var builders =
          new BedrockBuilderContext(clientBuilder, clientResultCaptor, chatModelBuilder);

      final var chatModel = provider.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(BedrockChatModel.class);
      assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

      verify(proxySupport).createAwsHttpClient(expectedEndpointOverride);
      builderAssertions.accept(builders);
    }
  }

  static Stream<BedrockModelParameters> nullModelParameters() {
    return Stream.of(new BedrockModelParameters(null, null, null));
  }

  private record BedrockBuilderContext(
      BedrockRuntimeClientBuilder clientBuilder,
      ResultCaptor<BedrockRuntimeClient> clientResultCaptor,
      BedrockChatModel.Builder chatModelBuilder) {}
}
