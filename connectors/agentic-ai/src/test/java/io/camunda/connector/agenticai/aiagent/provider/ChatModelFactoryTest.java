/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.ModelParameters;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {
  private static final ModelParameters DEFAULT_MODEL_PARAMETERS =
      new ModelParameters(10, 1.0, 0.8, 50);
  private static final ModelParameters NULL_MODEL_PARAMETERS =
      new ModelParameters(null, null, null, null);

  private final ChatModelFactory chatModelFactory = new ChatModelFactory();

  @Nested
  class AnthropicChatModelFactoryTest {

    private static final String ANTHROPIC_API_KEY = "myApiKey";
    private static final String ANTHROPIC_MODEL = "myModel";

    @Test
    void createsAnthropicChatModel() {
      final var providerConfig =
          new AnthropicProviderConfiguration(
              new AnthropicConnection(
                  null,
                  new AnthropicAuthentication(ANTHROPIC_API_KEY),
                  new AnthropicModel(ANTHROPIC_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testAnthropicChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).apiKey(ANTHROPIC_API_KEY);
            verify(builder).modelName(ANTHROPIC_MODEL);
            verify(builder, never()).baseUrl(any());
            verify(builder).maxTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
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
                  new AnthropicModel(ANTHROPIC_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testAnthropicChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).baseUrl("https://my-custom-endpoint.local");
          });
    }

    @ParameterizedTest
    @NullSource
    @ArgumentsSource(NullModelParametersArgumentsProvider.class)
    void createsAnthropicChatModelWithNullModelParameters(ModelParameters modelParameters) {
      final var providerConfig =
          new AnthropicProviderConfiguration(
              new AnthropicConnection(
                  null,
                  new AnthropicAuthentication(ANTHROPIC_API_KEY),
                  new AnthropicModel(ANTHROPIC_MODEL, modelParameters)));

      testAnthropicChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).apiKey(ANTHROPIC_API_KEY);
            verify(builder).modelName(ANTHROPIC_MODEL);
            verify(builder, never()).baseUrl(any());
            verify(builder, never()).maxTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            verify(builder, never()).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
            verify(builder, never()).topP(DEFAULT_MODEL_PARAMETERS.topP());
            verify(builder, never()).topK(DEFAULT_MODEL_PARAMETERS.topK());
          });
    }

    private void testAnthropicChatModelBuilder(
        AnthropicProviderConfiguration providerConfig,
        ThrowingConsumer<AnthropicChatModelBuilder> builderAssertions) {
      try (MockedStatic<AnthropicChatModel> chatModelMock =
          Mockito.mockStatic(AnthropicChatModel.class, Answers.CALLS_REAL_METHODS)) {
        var builder = spy(new AnthropicChatModelBuilder());
        chatModelMock.when(AnthropicChatModel::builder).thenReturn(builder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(AnthropicChatModel.class);

        builderAssertions.accept(builder);
      }
    }
  }

  private static class NullModelParametersArgumentsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(arguments(NULL_MODEL_PARAMETERS));
    }
  }
}
