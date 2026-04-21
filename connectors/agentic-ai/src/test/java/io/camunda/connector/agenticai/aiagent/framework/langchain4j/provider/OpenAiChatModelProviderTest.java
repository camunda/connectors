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
import static org.mockito.Mockito.doAnswer;
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
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel.OpenAiModelParameters;
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
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiChatModelProviderTest {

  private static final String OPEN_AI_API_KEY = "openAiApiKey";
  private static final String OPEN_AI_MODEL = "openAiModel";

  private static final OpenAiModelParameters DEFAULT_MODEL_PARAMETERS =
      new OpenAiModelParameters(10, 1.0, 0.8);

  private final ProxyConfiguration proxyConfiguration = ProxyConfiguration.NONE;
  private final ChatModelHttpProxySupport proxySupport =
      spy(
          new ChatModelHttpProxySupport(
              proxyConfiguration, new JdkHttpClientProxyConfigurator(proxyConfiguration)));

  private final OpenAiChatModelProvider provider =
      new OpenAiChatModelProvider(createDefaultConfigurationProperties(), proxySupport);

  @Captor private ArgumentCaptor<OpenAiChatRequestParameters> modelParametersArgumentCaptor;

  @Test
  void createsOpenAiChatModel() {
    final var providerConfig =
        new OpenAiProviderConfiguration(
            new OpenAiConnection(
                new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                MODEL_TIMEOUT,
                new OpenAiProviderConfiguration.OpenAiModel(
                    OPEN_AI_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testOpenAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).timeout(MODEL_TIMEOUT.timeout());
          verify(builder).apiKey(OPEN_AI_API_KEY);
          verify(builder).modelName(OPEN_AI_MODEL);
          verify(builder, never()).baseUrl(any());
          verify(builder, never()).organizationId(any());
          verify(builder, never()).projectId(any());

          verify(builder).defaultRequestParameters(modelParametersArgumentCaptor.capture());

          final var parameters = modelParametersArgumentCaptor.getValue();
          assertThat(parameters).isNotNull();
          assertThat(parameters.maxCompletionTokens())
              .isEqualTo(DEFAULT_MODEL_PARAMETERS.maxCompletionTokens());
          assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
          assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
        });
  }

  @Test
  void createsOpenAiChatModelWithCustomOrganizationAndProjectIds() {
    final var providerConfig =
        new OpenAiProviderConfiguration(
            new OpenAiConnection(
                new OpenAiProviderConfiguration.OpenAiAuthentication(
                    OPEN_AI_API_KEY, "MY_ORG_ID", "MY_PROJECT_ID"),
                MODEL_TIMEOUT,
                new OpenAiProviderConfiguration.OpenAiModel(
                    OPEN_AI_MODEL, DEFAULT_MODEL_PARAMETERS)));

    testOpenAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).organizationId("MY_ORG_ID");
          verify(builder).projectId("MY_PROJECT_ID");
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsOpenAiChatModelWithNullModelParameters(OpenAiModelParameters modelParameters) {
    final var providerConfig =
        new OpenAiProviderConfiguration(
            new OpenAiConnection(
                new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                MODEL_TIMEOUT,
                new OpenAiProviderConfiguration.OpenAiModel(OPEN_AI_MODEL, modelParameters)));

    testOpenAiChatModelBuilder(
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
          }
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void createsOpenAiChatModelWithUnspecifiedTimeouts(TimeoutConfiguration timeouts) {
    final var providerConfig =
        new OpenAiProviderConfiguration(
            new OpenAiConnection(
                new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                timeouts,
                new OpenAiProviderConfiguration.OpenAiModel(OPEN_AI_MODEL, null)));

    testOpenAiChatModelBuilder(
        providerConfig, (builder) -> verify(builder).timeout(Duration.ofMinutes(3)));
  }

  private void testOpenAiChatModelBuilder(
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
    return Stream.of(new OpenAiModelParameters(null, null, null));
  }
}
