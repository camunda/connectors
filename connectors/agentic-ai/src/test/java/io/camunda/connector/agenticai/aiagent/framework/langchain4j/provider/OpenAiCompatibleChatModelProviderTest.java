/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.MODEL_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.createDefaultChatModelProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.ApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication.OpenAiApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleChatModelProviderTest {

  private static final String API_KEY = "compatibleApiKey";
  private static final String ENDPOINT = "https://compatible.local/v1";
  private static final String MODEL = "some-compatible-model";

  private static final OpenAiModelParameters DEFAULT_MODEL_PARAMETERS =
      new OpenAiModelParameters(10, 1.0, 0.8, Map.of("my-param", "my-value"));

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration)));

  private final OpenAiCompatibleChatModelProvider provider =
      new OpenAiCompatibleChatModelProvider(createDefaultChatModelProperties(), proxySupport);

  @Captor private ArgumentCaptor<OpenAiChatRequestParameters> modelParametersArgumentCaptor;

  private OpenAiProviderConfiguration makeConfig(
      String apiKey,
      Map<String, String> headers,
      Map<String, String> queryParams,
      TimeoutConfiguration timeouts,
      OpenAiModelParameters modelParameters) {
    return new OpenAiProviderConfiguration(
        new OpenAiConnection(
            OpenAiBackend.CUSTOM,
            new OpenAiApiKeyAuthentication(apiKey, null, null),
            timeouts,
            new OpenAiModel(MODEL, modelParameters),
            ApiFamily.COMPLETIONS,
            ENDPOINT,
            headers,
            queryParams));
  }

  @Test
  void createsOpenAiCompatibleChatModelWithApiKeyAndHeaders() {
    final var providerConfig =
        makeConfig(
            API_KEY,
            Map.of("my-header", "my-value"),
            null,
            MODEL_TIMEOUT,
            DEFAULT_MODEL_PARAMETERS);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).timeout(MODEL_TIMEOUT.timeout());
          verify(builder).modelName(MODEL);
          verify(builder).baseUrl(ENDPOINT);
          verify(builder).apiKey(API_KEY);
          verify(builder).customHeaders(Map.of("my-header", "my-value"));

          verify(builder).defaultRequestParameters(modelParametersArgumentCaptor.capture());

          final var parameters = modelParametersArgumentCaptor.getValue();
          assertThat(parameters).isNotNull();
          assertThat(parameters.maxCompletionTokens())
              .isEqualTo(DEFAULT_MODEL_PARAMETERS.maxCompletionTokens());
          assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
          assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
          assertThat(parameters.customParameters())
              .isEqualTo(DEFAULT_MODEL_PARAMETERS.customParameters());

          // Ensure OpenAI-specific fields are not set
          verify(builder, never()).organizationId(any());
          verify(builder, never()).projectId(any());
        });
  }

  @Test
  void createsOpenAiCompatibleChatModelWithoutApiKey() {
    final var providerConfig =
        makeConfig(null, null, null, MODEL_TIMEOUT, DEFAULT_MODEL_PARAMETERS);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).modelName(MODEL);
          verify(builder).baseUrl(ENDPOINT);
          verify(builder, never()).apiKey(any());
          verify(builder, never()).customHeaders(anyMap());
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsOpenAiCompatibleChatModelWithNullModelParameters(
      OpenAiModelParameters modelParameters) {
    final var providerConfig =
        makeConfig(API_KEY, Map.of(), Map.of(), MODEL_TIMEOUT, modelParameters);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig,
        (builder) -> {
          if (modelParameters == null) {
            verify(builder, never()).defaultRequestParameters(any());
          } else {
            verify(builder).defaultRequestParameters(modelParametersArgumentCaptor.capture());

            final var parameters = modelParametersArgumentCaptor.getValue();
            assertThat(parameters).isNotNull().isInstanceOf(OpenAiChatRequestParameters.class);

            assertThat(parameters.maxCompletionTokens()).isNull();
            assertThat(parameters.temperature()).isNull();
            assertThat(parameters.topP()).isNull();
            assertThat(parameters.customParameters()).isEmpty();
          }
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsOpenAiCompatibleChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig = makeConfig(API_KEY, Map.of(), Map.of(), timeouts, null);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig, (builder) -> verify(builder).timeout(Duration.ofMinutes(3)));
  }

  @Test
  void createsOpenAiCompatibleChatModelWithApiKeyAndAuthorizationHeader() {
    final var authHeaderValue = "Bearer token123";
    final var providerConfig =
        makeConfig(
            API_KEY,
            Map.of("Authorization", authHeaderValue),
            Collections.emptyMap(),
            MODEL_TIMEOUT,
            DEFAULT_MODEL_PARAMETERS);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).modelName(MODEL);
          verify(builder).baseUrl(ENDPOINT);
          verify(builder).customHeaders(Map.of("Authorization", authHeaderValue));

          // API key set then cleared due to Authorization header
          final var ordered = inOrder(builder);
          ordered.verify(builder).apiKey(API_KEY);
          ordered.verify(builder).apiKey(null);

          verify(builder, never()).organizationId(any());
          verify(builder, never()).projectId(any());
        });
  }

  @Test
  void createsOpenAiCompatibleChatModelWithApiKeyAndHeaderAndQueryParameters() {
    final var authHeaderValue = "Bearer token123";
    final var customQueryParameters = Map.of("foo", "bar", "foo2", "bar2");
    final var providerConfig =
        makeConfig(
            API_KEY,
            Map.of("Authorization", authHeaderValue),
            customQueryParameters,
            null,
            DEFAULT_MODEL_PARAMETERS);

    testOpenAiCompatibleChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).modelName(MODEL);

          verify(builder).baseUrl(ENDPOINT);
          verify(builder).customQueryParams(customQueryParameters);
          verify(builder).customHeaders(Map.of("Authorization", authHeaderValue));

          // API key set then cleared due to Authorization header
          final var ordered = inOrder(builder);
          ordered.verify(builder).apiKey(API_KEY);
          ordered.verify(builder).apiKey(null);

          verify(builder, never()).organizationId(any());
          verify(builder, never()).projectId(any());
        });
  }

  private void testOpenAiCompatibleChatModelBuilder(
      OpenAiProviderConfiguration providerConfig,
      ThrowingConsumer<OpenAiChatModelBuilder> builderAssertions) {
    final var chatModelBuilder = spy(OpenAiChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<OpenAiChatModel> chatModelMock =
        mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      chatModelMock.when(OpenAiChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = provider.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(OpenAiChatModel.class);
      assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

      verify(proxySupport).createJdkHttpClientBuilder();
      builderAssertions.accept(chatModelBuilder);
    }
  }

  static Stream<OpenAiModelParameters> nullModelParameters() {
    return Stream.of(new OpenAiModelParameters(null, null, null, null));
  }
}
