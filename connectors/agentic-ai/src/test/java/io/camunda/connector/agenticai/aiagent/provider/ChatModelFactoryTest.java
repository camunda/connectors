/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.ModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import java.net.URI;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {
  private static final ModelParameters DEFAULT_MODEL_PARAMETERS =
      new ModelParameters(10, 1.0, 0.8, 50);
  private static final ModelParameters NULL_MODEL_PARAMETERS =
      new ModelParameters(null, null, null, null);

  private final ChatModelFactory chatModelFactory = new ChatModelFactory();

  @Nested
  class AnthropicChatModelFactoryTest {

    private static final String ANTHROPIC_API_KEY = "anthropicApiKey";
    private static final String ANTHROPIC_MODEL = "anthropicModel";

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
            verify(builder, never()).maxTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            verify(builder, never()).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
            verify(builder, never()).topP(DEFAULT_MODEL_PARAMETERS.topP());
            verify(builder, never()).topK(DEFAULT_MODEL_PARAMETERS.topK());
          });
    }

    private void testAnthropicChatModelBuilder(
        AnthropicProviderConfiguration providerConfig,
        ThrowingConsumer<AnthropicChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(AnthropicChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<AnthropicChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<AnthropicChatModel> chatModelMock =
          Mockito.mockStatic(AnthropicChatModel.class, Answers.CALLS_REAL_METHODS)) {
        chatModelMock.when(AnthropicChatModel::builder).thenReturn(chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(AnthropicChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        builderAssertions.accept(chatModelBuilder);
      }
    }
  }

  @Nested
  class BedrockChatModelFactoryTest {

    private static final String BEDROCK_REGION = "eu-west-1";
    private static final String BEDROCK_ACCESS_KEY = "bedrockAccessKey";
    private static final String BEDROCK_SECRET_KEY = "bedrockSecretKey";
    private static final String BEDROCK_MODEL = "bedrockModel";

    private static final ModelParameters BEDROCK_DEFAULT_MODEL_PARAMETERS =
        new ModelParameters(10, 1.0, 0.8, null);

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
                  new BedrockModel(BEDROCK_MODEL, BEDROCK_DEFAULT_MODEL_PARAMETERS)));

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
                  new AwsStaticCredentialsAuthentication(BEDROCK_ACCESS_KEY, BEDROCK_SECRET_KEY),
                  new BedrockModel(BEDROCK_MODEL, BEDROCK_DEFAULT_MODEL_PARAMETERS)));

      testCreateBedrockChatModelWithCredentials(
          providerConfig,
          (credentialsProvider) -> {
            assertThat(credentialsProvider)
                .isNotNull()
                .isInstanceOf(StaticCredentialsProvider.class);

            final var credentials = credentialsProvider.resolveCredentials();
            assertThat(credentials).isNotNull().isInstanceOf(AwsBasicCredentials.class);
            assertThat(credentials.accessKeyId()).isEqualTo(BEDROCK_ACCESS_KEY);
            assertThat(credentials.secretAccessKey()).isEqualTo(BEDROCK_SECRET_KEY);
          });
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
            assertThat(parameters.maxOutputTokens())
                .isEqualTo(BEDROCK_DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            assertThat(parameters.temperature())
                .isEqualTo(BEDROCK_DEFAULT_MODEL_PARAMETERS.temperature());
            assertThat(parameters.topP()).isEqualTo(BEDROCK_DEFAULT_MODEL_PARAMETERS.topP());
            assertThat(parameters.topK()).isEqualTo(BEDROCK_DEFAULT_MODEL_PARAMETERS.topK());
          });
    }

    @Test
    void createsBedrockChatModelWithCustomEndpoint() {
      final var providerConfig =
          new BedrockProviderConfiguration(
              new BedrockConnection(
                  BEDROCK_REGION,
                  "https://my-custom-endpoint.local",
                  new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                  new BedrockModel(BEDROCK_MODEL, BEDROCK_DEFAULT_MODEL_PARAMETERS)));

      testBedrockChatModelBuilder(
          providerConfig,
          (builders) -> {
            verify(builders.clientBuilder)
                .endpointOverride(URI.create("https://my-custom-endpoint.local"));
          });
    }

    @Test
    void throwsExceptionWhenCreatingAModelWithUnsupportedTopKValue() {
      final var providerConfig =
          new BedrockProviderConfiguration(
              new BedrockConnection(
                  BEDROCK_REGION,
                  null,
                  new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                  new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

      assertThatThrownBy(() -> testBedrockChatModelBuilder(providerConfig, (builders) -> {}))
          .isInstanceOf(UnsupportedFeatureException.class)
          .hasMessageContaining("'topK' parameter is not supported yet by this model provider");
    }

    @ParameterizedTest
    @NullSource
    @ArgumentsSource(NullModelParametersArgumentsProvider.class)
    void createsBedrockChatModelWithNullModelParameters(ModelParameters modelParameters) {
      final var providerConfig =
          new BedrockProviderConfiguration(
              new BedrockConnection(
                  BEDROCK_REGION,
                  null,
                  new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
                  new BedrockModel(BEDROCK_MODEL, modelParameters)));

      testBedrockChatModelBuilder(
          providerConfig,
          (builders) -> {
            verify(builders.chatModelBuilder)
                .defaultRequestParameters(modelParametersArgumentCaptor.capture());

            final var parameters = modelParametersArgumentCaptor.getValue();
            assertThat(parameters).isNotNull().isInstanceOf(BedrockChatRequestParameters.class);
            assertThat(parameters.maxOutputTokens()).isNull();
            assertThat(parameters.temperature()).isNull();
            assertThat(parameters.topP()).isNull();
            assertThat(parameters.topK()).isNull();
          });
    }

    private void testBedrockChatModelBuilder(
        BedrockProviderConfiguration providerConfig,
        ThrowingConsumer<BedrockBuilderContext> builderAssertions) {
      final var clientBuilder = spy(BedrockRuntimeClient.builder());
      final var clientResultCaptor = new ResultCaptor<BedrockRuntimeClient>();
      doAnswer(clientResultCaptor).when(clientBuilder).build();

      final var chatModelBuilder = spy(BedrockChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<BedrockRuntimeClient> clientMock =
              Mockito.mockStatic(BedrockRuntimeClient.class, Answers.CALLS_REAL_METHODS);
          MockedStatic<BedrockChatModel> chatModelMock =
              Mockito.mockStatic(BedrockChatModel.class, Answers.CALLS_REAL_METHODS)) {
        clientMock.when(BedrockRuntimeClient::builder).thenReturn(clientBuilder);
        chatModelMock.when(BedrockChatModel::builder).thenReturn(chatModelBuilder);

        final var builders =
            new BedrockBuilderContext(clientBuilder, clientResultCaptor, chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(BedrockChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        builderAssertions.accept(builders);
      }
    }

    private record BedrockBuilderContext(
        BedrockRuntimeClientBuilder clientBuilder,
        ResultCaptor<BedrockRuntimeClient> clientResultCaptor,
        BedrockChatModel.Builder chatModelBuilder) {}
  }

  @Nested
  class OpenAiChatModelFactoryTest {

    private static final String OPEN_AI_API_KEY = "openAiApiKey";
    private static final String OPEN_AI_MODEL = "openAiModel";

    @Captor private ArgumentCaptor<OpenAiChatRequestParameters> modelParametersArgumentCaptor;

    @Test
    void createsOpenAiChatModel() {
      final var providerConfig =
          new OpenAiProviderConfiguration(
              new OpenAiConnection(
                  null,
                  new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                  new OpenAiProviderConfiguration.OpenAiModel(
                      OPEN_AI_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testOpenAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).apiKey(OPEN_AI_API_KEY);
            verify(builder).modelName(OPEN_AI_MODEL);
            verify(builder, never()).baseUrl(any());
            verify(builder, never()).organizationId(any());
            verify(builder, never()).projectId(any());

            verify(builder).defaultRequestParameters(modelParametersArgumentCaptor.capture());

            final var parameters = modelParametersArgumentCaptor.getValue();
            assertThat(parameters).isNotNull();
            assertThat(parameters.maxOutputTokens())
                .isEqualTo(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
            assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
            assertThat(parameters.topK()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topK());
          });
    }

    @Test
    void createsOpenAiChatModelWithCustomOrganizationAndProject() {
      final var providerConfig =
          new OpenAiProviderConfiguration(
              new OpenAiConnection(
                  null,
                  new OpenAiProviderConfiguration.OpenAiAuthentication(
                      OPEN_AI_API_KEY, "MY_ORG", "MY_PROJECT"),
                  new OpenAiProviderConfiguration.OpenAiModel(
                      OPEN_AI_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testOpenAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).organizationId("MY_ORG");
            verify(builder).projectId("MY_PROJECT");
          });
    }

    @Test
    void createsOpenAiChatModelWithCustomEndpoint() {
      final var providerConfig =
          new OpenAiProviderConfiguration(
              new OpenAiConnection(
                  "https://my-custom-endpoint.local",
                  new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                  new OpenAiProviderConfiguration.OpenAiModel(
                      OPEN_AI_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testOpenAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).baseUrl("https://my-custom-endpoint.local");
          });
    }

    @ParameterizedTest
    @NullSource
    @ArgumentsSource(NullModelParametersArgumentsProvider.class)
    void createsOpenAiChatModelWithNullModelParameters(ModelParameters modelParameters) {
      final var providerConfig =
          new OpenAiProviderConfiguration(
              new OpenAiConnection(
                  null,
                  new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
                  new OpenAiProviderConfiguration.OpenAiModel(OPEN_AI_MODEL, modelParameters)));

      testOpenAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).defaultRequestParameters(modelParametersArgumentCaptor.capture());

            final var parameters = modelParametersArgumentCaptor.getValue();
            assertThat(parameters).isNotNull().isInstanceOf(OpenAiChatRequestParameters.class);

            assertThat(parameters.maxOutputTokens()).isNull();
            assertThat(parameters.temperature()).isNull();
            assertThat(parameters.topP()).isNull();
            assertThat(parameters.topK()).isNull();
          });
    }

    private void testOpenAiChatModelBuilder(
        OpenAiProviderConfiguration providerConfig,
        ThrowingConsumer<OpenAiChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(OpenAiChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<OpenAiChatModel> chatModelMock =
          Mockito.mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
        chatModelMock.when(OpenAiChatModel::builder).thenReturn(chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(OpenAiChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        builderAssertions.accept(chatModelBuilder);
      }
    }
  }

  private static class NullModelParametersArgumentsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(arguments(NULL_MODEL_PARAMETERS));
    }
  }

  private static class ResultCaptor<T> implements Answer<T> {
    private T result = null;

    public T getResult() {
      return result;
    }

    @Override
    public T answer(InvocationOnMock invocationOnMock) throws Throwable {
      //noinspection unchecked
      result = (T) invocationOnMock.callRealMethod();
      return result;
    }
  }
}
