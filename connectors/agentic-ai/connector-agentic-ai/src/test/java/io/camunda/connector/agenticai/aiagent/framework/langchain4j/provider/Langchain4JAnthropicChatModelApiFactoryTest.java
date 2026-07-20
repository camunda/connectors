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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JAnthropicChatModelApiFactoryTest {

  private static final String ANTHROPIC_API_KEY = "anthropicApiKey";
  private static final String ANTHROPIC_MODEL = "anthropicModel";

  private static final AnthropicModelParameters DEFAULT_MODEL_PARAMETERS =
      new AnthropicModelParameters(10, 1.0, 0.8, 50);

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              new HttpTransportSupport(
                  proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration))));

  private final Langchain4JAnthropicChatModelApiFactory provider =
      new Langchain4JAnthropicChatModelApiFactory(
          createDefaultChatModelProperties(),
          proxySupport,
          mock(ChatMessageConverter.class),
          mock(ToolSpecificationConverter.class),
          mock(JsonSchemaConverter.class),
          Langchain4JChatModelApi.DEFAULT_CAPABILITIES);

  @Test
  void createsAnthropicChatModel() {
    final var providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication(ANTHROPIC_API_KEY),
                MODEL_TIMEOUT,
                new AnthropicModel(ANTHROPIC_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testAnthropicChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).timeout(MODEL_TIMEOUT.timeout());
          verify(builder).apiKey(ANTHROPIC_API_KEY);
          verify(builder).modelName(ANTHROPIC_MODEL);
          verify(builder, never()).baseUrl(any());
          verify(builder).maxTokens(DEFAULT_MODEL_PARAMETERS.maxTokens());
          verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
          verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
          verify(builder).topK(DEFAULT_MODEL_PARAMETERS.topK());
        });
  }

  @Test
  void createsAnthropicChatModelWithCustomEndpoint() {
    final var providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                "https://my-custom-endpoint.local",
                new AnthropicAuthentication(ANTHROPIC_API_KEY),
                MODEL_TIMEOUT,
                new AnthropicModel(ANTHROPIC_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testAnthropicChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).baseUrl("https://my-custom-endpoint.local");
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsAnthropicChatModelWithNullModelParameters(AnthropicModelParameters modelParameters) {
    final var providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication(ANTHROPIC_API_KEY),
                MODEL_TIMEOUT,
                new AnthropicModel(ANTHROPIC_MODEL, modelParameters)));

    testAnthropicChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder, never()).maxTokens(anyInt());
          verify(builder, never()).temperature(anyDouble());
          verify(builder, never()).topP(anyDouble());
          verify(builder, never()).topK(anyInt());
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsAnthropicChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication(ANTHROPIC_API_KEY),
                timeouts,
                new AnthropicModel(ANTHROPIC_MODEL, null)));

    testAnthropicChatModelBuilder(
        providerConfig, (builder) -> verify(builder).timeout(Duration.ofMinutes(3)));
  }

  @Test
  void closingChatModelClosesHttpClient() throws Exception {
    final var providerConfig =
        new AnthropicProviderConfiguration(
            new AnthropicConnection(
                null,
                new AnthropicAuthentication(ANTHROPIC_API_KEY),
                MODEL_TIMEOUT,
                new AnthropicModel(ANTHROPIC_MODEL, null)));

    final var mockHttpClient = mock(HttpClient.class);
    final var mockBuilder = mock(HttpClient.Builder.class, Answers.RETURNS_SELF);
    when(mockBuilder.build()).thenReturn(mockHttpClient);

    try (MockedStatic<HttpClient> httpClientMock =
            mockStatic(HttpClient.class, Answers.CALLS_REAL_METHODS);
        MockedStatic<AnthropicChatModel> chatModelMock =
            mockStatic(AnthropicChatModel.class, Answers.CALLS_REAL_METHODS)) {
      httpClientMock.when(HttpClient::newBuilder).thenReturn(mockBuilder);

      final var chatModel = (CloseableChatModel) provider.createChatModel(providerConfig);
      assertThat(chatModel).isInstanceOf(CloseableChatModel.class);

      chatModel.close();

      verify(mockHttpClient).close();
    }
  }

  private void testAnthropicChatModelBuilder(
      AnthropicProviderConfiguration providerConfig,
      ThrowingConsumer<AnthropicChatModelBuilder> builderAssertions) {
    final var chatModelBuilder = spy(AnthropicChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<AnthropicChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<AnthropicChatModel> chatModelMock =
        mockStatic(AnthropicChatModel.class, Answers.CALLS_REAL_METHODS)) {
      chatModelMock.when(AnthropicChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = provider.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(CloseableChatModelDelegate.class);
      assertThat(((CloseableChatModelDelegate) chatModel).delegate())
          .isSameAs(chatModelResultCaptor.getResult());

      verify(proxySupport).createJdkHttpClientBuilder();
      builderAssertions.accept(chatModelBuilder);
    }
  }

  static Stream<AnthropicModelParameters> nullModelParameters() {
    return Stream.of(new AnthropicModelParameters(null, null, null, null));
  }

  @Test
  void mapsAnthropicCacheTokenCounts() {
    final var usage =
        AnthropicTokenUsage.builder()
            .inputTokenCount(5)
            .outputTokenCount(6)
            .cacheReadInputTokens(2)
            .cacheCreationInputTokens(3)
            .build();

    assertThat(provider.mapTokenUsage(usage))
        .usingRecursiveComparison()
        .isEqualTo(
            AgentMetrics.TokenUsage.empty()
                .withInputTokenCount(5)
                .withOutputTokenCount(6)
                .withCacheReadTokenCount(2)
                .withCacheCreationTokenCount(3));
  }

  @Test
  void mapsAnthropicCacheTokenCountsWhenNullOnTheVendorSide() {
    final var usage = AnthropicTokenUsage.builder().inputTokenCount(5).outputTokenCount(6).build();

    assertThat(provider.mapTokenUsage(usage))
        .usingRecursiveComparison()
        .isEqualTo(AgentMetrics.TokenUsage.empty().withInputTokenCount(5).withOutputTokenCount(6));
  }

  @Test
  void fallsBackToBaseMappingForNonAnthropicTokenUsage() {
    final var usage = new TokenUsage(5, 6);

    assertThat(provider.mapTokenUsage(usage))
        .usingRecursiveComparison()
        .isEqualTo(AgentMetrics.TokenUsage.empty().withInputTokenCount(5).withOutputTokenCount(6));
  }

  @Test
  void mapsEmptyTokenUsageWhenMissing() {
    assertThat(provider.mapTokenUsage(null))
        .usingRecursiveComparison()
        .isEqualTo(AgentMetrics.TokenUsage.empty());
  }
}
