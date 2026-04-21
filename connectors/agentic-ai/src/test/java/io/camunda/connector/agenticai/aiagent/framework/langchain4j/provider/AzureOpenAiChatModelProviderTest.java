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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiModel.AzureOpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureOpenAiChatModelProviderTest {

  private static final String AZURE_OPENAI_API_KEY = "azureOpenAiApiKey";
  private static final String AZURE_OPENAI_ENDPOINT = "https://azure-openai-endpoint.local";
  private static final String AZURE_OPENAI_DEPLOYMENT_NAME = "gpt-4o";
  private static final String CLIENT_ID = "clientId";
  private static final String CLIENT_SECRET = "clientSecret";
  private static final String TENANT_ID = "tenantId";

  private static final AzureOpenAiModelParameters DEFAULT_MODEL_PARAMETERS =
      new AzureOpenAiModelParameters(10, 1.0, 0.8);

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration)));

  private final AzureOpenAiChatModelProvider provider =
      new AzureOpenAiChatModelProvider(createDefaultConfigurationProperties(), proxySupport);

  @Captor ArgumentCaptor<TokenCredential> tokenCredentialsCapture;

  @Test
  void createsAzureOpenAiChatModelWithApiKey() {
    final var providerConfig =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                AZURE_OPENAI_ENDPOINT,
                new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
                MODEL_TIMEOUT,
                new AzureOpenAiProviderConfiguration.AzureOpenAiModel(
                    AZURE_OPENAI_DEPLOYMENT_NAME, DEFAULT_MODEL_PARAMETERS)));

    testAzureOpenAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).timeout(MODEL_TIMEOUT.timeout());
          verify(builder).apiKey(AZURE_OPENAI_API_KEY);
          verify(builder).maxTokens(DEFAULT_MODEL_PARAMETERS.maxTokens());
          verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
          verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
          verify(builder, never()).tokenCredential(any());
        });
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"https://some-authortiy-host"})
  void createsAzureOpenAiChatModelWithClientCredentials(String authorityHost) {
    final var providerConfig =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                AZURE_OPENAI_ENDPOINT,
                new AzureClientCredentialsAuthentication(
                    CLIENT_ID, CLIENT_SECRET, TENANT_ID, authorityHost),
                MODEL_TIMEOUT,
                new AzureOpenAiProviderConfiguration.AzureOpenAiModel(
                    AZURE_OPENAI_DEPLOYMENT_NAME, DEFAULT_MODEL_PARAMETERS)));

    testAzureOpenAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder, never()).apiKey(any());
          verify(builder).maxTokens(DEFAULT_MODEL_PARAMETERS.maxTokens());
          verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
          verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
          verify(builder).tokenCredential(tokenCredentialsCapture.capture());
          final var tokenCredential = tokenCredentialsCapture.getValue();
          assertThat(tokenCredential).isNotNull().isInstanceOf(ClientSecretCredential.class);
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsAzureOpenAiChatModelWithNullModelParameters(
      AzureOpenAiModelParameters modelParameters) {
    final var providerConfig =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                AZURE_OPENAI_ENDPOINT,
                new AzureClientCredentialsAuthentication(CLIENT_ID, CLIENT_SECRET, TENANT_ID, null),
                MODEL_TIMEOUT,
                new AzureOpenAiProviderConfiguration.AzureOpenAiModel(
                    AZURE_OPENAI_DEPLOYMENT_NAME, modelParameters)));

    testAzureOpenAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder, never()).maxTokens(anyInt());
          verify(builder, never()).temperature(anyDouble());
          verify(builder, never()).topP(anyDouble());
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsAzureOpenAiChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig =
        new AzureOpenAiProviderConfiguration(
            new AzureOpenAiConnection(
                AZURE_OPENAI_ENDPOINT,
                new AzureClientCredentialsAuthentication(CLIENT_ID, CLIENT_SECRET, TENANT_ID, null),
                timeouts,
                new AzureOpenAiProviderConfiguration.AzureOpenAiModel(
                    AZURE_OPENAI_DEPLOYMENT_NAME, null)));

    testAzureOpenAiChatModelBuilder(
        providerConfig, (builder) -> verify(builder).timeout(Duration.ofMinutes(3)));
  }

  private void testAzureOpenAiChatModelBuilder(
      AzureOpenAiProviderConfiguration providerConfig,
      ThrowingConsumer<AzureOpenAiChatModel.Builder> builderAssertions) {
    final var chatModelBuilder = spy(AzureOpenAiChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<AzureOpenAiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<AzureOpenAiChatModel> chatModelMock =
        mockStatic(AzureOpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      chatModelMock.when(AzureOpenAiChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = provider.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(AzureOpenAiChatModel.class);
      assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

      verify(proxySupport).createAzureProxyOptions(AZURE_OPENAI_ENDPOINT);
      verify(chatModelBuilder).endpoint(AZURE_OPENAI_ENDPOINT);
      verify(chatModelBuilder).deploymentName(AZURE_OPENAI_DEPLOYMENT_NAME);
      builderAssertions.accept(chatModelBuilder);
    }
  }

  static Stream<AzureOpenAiModelParameters> nullModelParameters() {
    return Stream.of(new AzureOpenAiModelParameters(null, null, null));
  }
}
