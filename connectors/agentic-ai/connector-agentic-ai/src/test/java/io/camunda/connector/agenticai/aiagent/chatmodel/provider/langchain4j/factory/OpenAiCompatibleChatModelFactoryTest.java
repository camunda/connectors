/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.MODEL_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.createDefaultChatModelProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel.OpenAiCompatibleModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.http.HttpClient;
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
class OpenAiCompatibleChatModelFactoryTest {

  private static final String API_KEY = "compatibleApiKey";
  private static final String ENDPOINT = "https://compatible.local/v1";
  private static final String MODEL = "some-compatible-model";

  private static final OpenAiCompatibleModelParameters DEFAULT_MODEL_PARAMETERS =
      new OpenAiCompatibleModelParameters(10, 1.0, 0.8, Map.of("my-param", "my-value"));

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration)));

  private final OpenAiCompatibleChatModelFactory factory =
      new OpenAiCompatibleChatModelFactory(
          createDefaultChatModelProperties(),
          proxySupport,
          mock(ChatMessageConverter.class),
          mock(ToolSpecificationConverter.class),
          mock(JsonSchemaConverter.class));

  @Captor private ArgumentCaptor<OpenAiChatRequestParameters> modelParametersArgumentCaptor;

  @Test
  void createsOpenAiCompatibleChatModelWithApiKeyAndHeaders() {
    final var providerConfig =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(API_KEY),
                Map.of("my-header", "my-value"),
                null,
                MODEL_TIMEOUT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                    MODEL, DEFAULT_MODEL_PARAMETERS)));

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
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(null),
                null,
                null,
                MODEL_TIMEOUT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                    MODEL, DEFAULT_MODEL_PARAMETERS)));

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
      OpenAiCompatibleModelParameters modelParameters) {
    final var providerConfig =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(API_KEY),
                Map.of(),
                Map.of(),
                MODEL_TIMEOUT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                    MODEL, modelParameters)));

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
      "io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsOpenAiCompatibleChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(API_KEY),
                Map.of(),
                Map.of(),
                timeouts,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(MODEL, null)));

    testOpenAiCompatibleChatModelBuilder(
        providerConfig, (builder) -> verify(builder).timeout(Duration.ofMinutes(3)));
  }

  @Test
  void createsOpenAiCompatibleChatModelWithApiKeyAndAuthorizationHeader() {
    final var authHeaderValue = "Bearer token123";
    final var providerConfig =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(API_KEY),
                Map.of("Authorization", authHeaderValue),
                Collections.emptyMap(),
                MODEL_TIMEOUT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                    MODEL, DEFAULT_MODEL_PARAMETERS)));

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
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication(API_KEY),
                Map.of("Authorization", authHeaderValue),
                customQueryParameters,
                null,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(
                    MODEL, DEFAULT_MODEL_PARAMETERS)));

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

  @Test
  void closingChatModelClosesHttpClient() throws Exception {
    final var providerConfig =
        new OpenAiCompatibleProviderConfiguration(
            new OpenAiCompatibleConnection(
                ENDPOINT,
                null,
                null,
                null,
                MODEL_TIMEOUT,
                new OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel(MODEL, null)));

    final var mockHttpClient = mock(HttpClient.class);
    final var mockBuilder = mock(HttpClient.Builder.class, Answers.RETURNS_SELF);
    when(mockBuilder.build()).thenReturn(mockHttpClient);

    try (MockedStatic<HttpClient> httpClientMock =
            mockStatic(HttpClient.class, Answers.CALLS_REAL_METHODS);
        MockedStatic<OpenAiChatModel> chatModelMock =
            mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      httpClientMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);

      final var chatModel = (CloseableChatModel) factory.createChatModel(providerConfig);
      assertThat(chatModel).isInstanceOf(CloseableChatModel.class);

      chatModel.close();

      verify(mockHttpClient).close();
    }
  }

  @Test
  void mapsOpenAiTokenUsageWithCacheAndReasoningTokenDetail() {
    final var usage =
        OpenAiTokenUsage.builder()
            .inputTokenCount(10)
            .outputTokenCount(20)
            .inputTokensDetails(
                OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(5).build())
            .outputTokensDetails(
                OpenAiTokenUsage.OutputTokensDetails.builder().reasoningTokens(7).build())
            .build();

    final var tokenUsage = factory.mapTokenUsage(usage);

    // OpenAI's prompt_tokens (here: inputTokenCount) includes cached tokens; the domain model
    // keeps inputTokenCount and cacheReadTokenCount disjoint, so cached tokens are subtracted back
    // out of inputTokenCount.
    assertThat(tokenUsage).isEqualTo(new AgentMetrics.TokenUsage(5, 20, 5, 0, 7));
  }

  @Test
  void fallsBackToBaseMappingForNonOpenAiTokenUsage() {
    final var usage = new TokenUsage(10, 20);

    final var tokenUsage = factory.mapTokenUsage(usage);

    assertThat(tokenUsage).isEqualTo(new AgentMetrics.TokenUsage(10, 20));
  }

  private void testOpenAiCompatibleChatModelBuilder(
      OpenAiCompatibleProviderConfiguration providerConfig,
      ThrowingConsumer<OpenAiChatModelBuilder> builderAssertions) {
    final var chatModelBuilder = spy(OpenAiChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<OpenAiChatModel> chatModelMock =
        mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      chatModelMock.when(OpenAiChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = factory.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(CloseableChatModelDelegate.class);
      assertThat(((CloseableChatModelDelegate) chatModel).delegate())
          .isSameAs(chatModelResultCaptor.getResult());

      verify(proxySupport).createJdkHttpClientBuilder();
      builderAssertions.accept(chatModelBuilder);
    }
  }

  static Stream<OpenAiCompatibleModelParameters> nullModelParameters() {
    return Stream.of(new OpenAiCompatibleModelParameters(null, null, null, null));
  }
}
