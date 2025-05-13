/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.BedrockModel.BedrockModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import java.net.URI;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
  private final ChatModelFactory chatModelFactory = new ChatModelFactory();

  @Nested
  class AnthropicChatModelFactoryTest {

    private static final String ANTHROPIC_API_KEY = "anthropicApiKey";
    private static final String ANTHROPIC_MODEL = "anthropicModel";

    private static final AnthropicModelParameters DEFAULT_MODEL_PARAMETERS =
        new AnthropicModelParameters(10, 1.0, 0.8, 50);

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
    void createsAnthropicChatModelWithNullModelParameters(
        AnthropicModelParameters modelParameters) {
      final var providerConfig =
          new AnthropicProviderConfiguration(
              new AnthropicConnection(
                  null,
                  new AnthropicAuthentication(ANTHROPIC_API_KEY),
                  new AnthropicModel(ANTHROPIC_MODEL, modelParameters)));

      testAnthropicChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder, never()).maxTokens(DEFAULT_MODEL_PARAMETERS.maxTokens());
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

    static Stream<AnthropicModelParameters> nullModelParameters() {
      return Stream.of(new AnthropicModelParameters(null, null, null, null));
    }
  }

  @Nested
  class BedrockChatModelFactoryTest {

    private static final String BEDROCK_REGION = "eu-west-1";
    private static final String BEDROCK_ACCESS_KEY = "bedrockAccessKey";
    private static final String BEDROCK_SECRET_KEY = "bedrockSecretKey";
    private static final String BEDROCK_MODEL = "bedrockModel";

    private static final BedrockModelParameters DEFAULT_MODEL_PARAMETERS =
        new BedrockModelParameters(10, 1.0, 0.8);

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
                  new AwsStaticCredentialsAuthentication(BEDROCK_ACCESS_KEY, BEDROCK_SECRET_KEY),
                  new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

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
                .isEqualTo(DEFAULT_MODEL_PARAMETERS.maxTokens());
            assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
            assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
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
                  new BedrockModel(BEDROCK_MODEL, DEFAULT_MODEL_PARAMETERS)));

      testBedrockChatModelBuilder(
          providerConfig,
          (builders) -> {
            verify(builders.clientBuilder)
                .endpointOverride(URI.create("https://my-custom-endpoint.local"));
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

    static Stream<BedrockModelParameters> nullModelParameters() {
      return Stream.of(new BedrockModelParameters(null, null, null));
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

    private static final OpenAiModelParameters DEFAULT_MODEL_PARAMETERS =
        new OpenAiModelParameters(10, 1.0, 0.8);

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
            assertThat(parameters.maxCompletionTokens())
                .isEqualTo(DEFAULT_MODEL_PARAMETERS.maxCompletionTokens());
            assertThat(parameters.temperature()).isEqualTo(DEFAULT_MODEL_PARAMETERS.temperature());
            assertThat(parameters.topP()).isEqualTo(DEFAULT_MODEL_PARAMETERS.topP());
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
    @MethodSource("nullModelParameters")
    void createsOpenAiChatModelWithNullModelParameters(OpenAiModelParameters modelParameters) {
      final var providerConfig =
          new OpenAiProviderConfiguration(
              new OpenAiConnection(
                  null,
                  new OpenAiProviderConfiguration.OpenAiAuthentication(OPEN_AI_API_KEY, null, null),
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

    static Stream<OpenAiModelParameters> nullModelParameters() {
      return Stream.of(new OpenAiModelParameters(null, null, null));
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
