/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureOpenAiModel.AzureOpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BedrockModel.BedrockModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel.OpenAiCompatibleModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.mixin.TimeoutConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

@ExtendWith(MockitoExtension.class)
class ChatModelFactoryTest {
  private static final TimeoutConfiguration MODEL_TIMEOUT =
      new TimeoutConfiguration(Duration.ofSeconds(30));

  private final ChatModelFactory chatModelFactory = new ChatModelFactoryImpl();

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
    void createsAnthropicChatModelWithNullModelParameters(
        AnthropicModelParameters modelParameters) {
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

    private void testAnthropicChatModelBuilder(
        AnthropicProviderConfiguration providerConfig,
        ThrowingConsumer<AnthropicChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(AnthropicChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<AnthropicChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<AnthropicChatModel> chatModelMock =
          mockStatic(AnthropicChatModel.class, Answers.CALLS_REAL_METHODS)) {
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
  class AzureOpenAiChatModelFactoryTest {

    private static final String AZURE_OPENAI_API_KEY = "azureOpenAiApiKey";
    private static final String AZURE_OPENAI_ENDPOINT = "azure-openai-endpoint.local";
    private static final String AZURE_OPENAI_DEPLOYMENT_NAME = "gpt-4o";
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String TENANT_ID = "tenantId";

    private static final AzureOpenAiModelParameters DEFAULT_MODEL_PARAMETERS =
        new AzureOpenAiModelParameters(10, 1.0, 0.8);

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
                  new AzureClientCredentialsAuthentication(
                      CLIENT_ID, CLIENT_SECRET, TENANT_ID, null),
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

    private void testAzureOpenAiChatModelBuilder(
        AzureOpenAiProviderConfiguration providerConfig,
        ThrowingConsumer<AzureOpenAiChatModel.Builder> builderAssertions) {
      final var chatModelBuilder = spy(AzureOpenAiChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<AzureOpenAiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<AzureOpenAiChatModel> chatModelMock =
          mockStatic(AzureOpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
        chatModelMock.when(AzureOpenAiChatModel::builder).thenReturn(chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(AzureOpenAiChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        verify(chatModelBuilder).endpoint(AZURE_OPENAI_ENDPOINT);
        verify(chatModelBuilder).deploymentName(AZURE_OPENAI_DEPLOYMENT_NAME);
        builderAssertions.accept(chatModelBuilder);
      }
    }

    static Stream<AzureOpenAiModelParameters> nullModelParameters() {
      return Stream.of(new AzureOpenAiModelParameters(null, null, null));
    }
  }

  @Nested
  class BedrockChatModelFactoryTest {

    private static final String BEDROCK_REGION = "eu-west-1";
    private static final String BEDROCK_API_KEY = "bedrockApiKey";
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
            assertThat(credentialsProvider)
                .isNotNull()
                .isInstanceOf(StaticCredentialsProvider.class);

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
    void createsBedrockChatModelWithCustomEndpoint() {
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
              mockStatic(BedrockRuntimeClient.class, Answers.CALLS_REAL_METHODS);
          MockedStatic<BedrockChatModel> chatModelMock =
              mockStatic(BedrockChatModel.class, Answers.CALLS_REAL_METHODS)) {
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
  class GoogleVertexAiChatModelFactoryTest {

    private static final String PROJECT_ID = "projectId";
    private static final String REGION = "us-central1";
    private static final String MODEL = "gemini-2.5-pro";

    private static final GoogleVertexAiModelParameters DEFAULT_MODEL_PARAMETERS =
        new GoogleVertexAiModelParameters(10, 1.0F, 0.8F, 100);

    @Test
    void createsGoogleVertexAiChatModel() {
      final var providerConfig =
          new GoogleVertexAiProviderConfiguration(
              new GoogleVertexAiConnection(
                  PROJECT_ID,
                  REGION,
                  new ApplicationDefaultCredentialsAuthentication(),
                  new GoogleVertexAiModel(MODEL, DEFAULT_MODEL_PARAMETERS)));

      testGoogleVertexAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).location(REGION);
            verify(builder).project(PROJECT_ID);
            verify(builder).modelName(MODEL);
            verify(builder).maxOutputTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
            verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
            verify(builder).topK(DEFAULT_MODEL_PARAMETERS.topK());
          });
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("nullModelParameters")
    void createsGoogleVertexAiChatModelWithNullModelParameters(
        GoogleVertexAiModelParameters modelParameters) {
      final var providerConfig =
          new GoogleVertexAiProviderConfiguration(
              new GoogleVertexAiConnection(
                  PROJECT_ID,
                  REGION,
                  new ApplicationDefaultCredentialsAuthentication(),
                  new GoogleVertexAiModel(MODEL, modelParameters)));

      testGoogleVertexAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder, never()).maxOutputTokens(anyInt());
            verify(builder, never()).temperature(anyFloat());
            verify(builder, never()).topP(anyFloat());
            verify(builder, never()).topK(anyInt());
          });
    }

    @Test
    void createsGoogleVertexAiChatModelWithServiceAccountCredential() {
      final var providerConfig =
          new GoogleVertexAiProviderConfiguration(
              new GoogleVertexAiConnection(
                  PROJECT_ID,
                  REGION,
                  new ServiceAccountCredentialsAuthentication("{}"),
                  new GoogleVertexAiModel(MODEL, DEFAULT_MODEL_PARAMETERS)));

      try (final var staticMockedSac = mockStatic(ServiceAccountCredentials.class)) {
        final var mockedSac = mock(ServiceAccountCredentials.class);
        when(mockedSac.createScoped(anyString())).thenReturn(mockedSac);
        staticMockedSac
            .when(() -> ServiceAccountCredentials.fromStream(any()))
            .thenReturn(mockedSac);

        testGoogleVertexAiChatModelBuilder(
            providerConfig,
            (builder) -> {
              verify(builder).location(REGION);
              verify(builder).project(PROJECT_ID);
              verify(builder).credentials(mockedSac);
              verify(builder).modelName(MODEL);
              verify(builder).maxOutputTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
              verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
              verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
              verify(builder).topK(DEFAULT_MODEL_PARAMETERS.topK());
            });

        staticMockedSac.verify(() -> ServiceAccountCredentials.fromStream(any()));
      }
    }

    private void testGoogleVertexAiChatModelBuilder(
        GoogleVertexAiProviderConfiguration providerConfig,
        ThrowingConsumer<VertexAiGeminiChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(VertexAiGeminiChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<VertexAiGeminiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<VertexAiGeminiChatModel> chatModelMock =
          mockStatic(VertexAiGeminiChatModel.class, Answers.CALLS_REAL_METHODS)) {
        chatModelMock.when(VertexAiGeminiChatModel::builder).thenReturn(chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(VertexAiGeminiChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        builderAssertions.accept(chatModelBuilder);
      }
    }

    static Stream<GoogleVertexAiModelParameters> nullModelParameters() {
      return Stream.of(new GoogleVertexAiModelParameters(null, null, null, null));
    }
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

    private void testOpenAiChatModelBuilder(
        OpenAiProviderConfiguration providerConfig,
        ThrowingConsumer<OpenAiChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(OpenAiChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<OpenAiChatModel> chatModelMock =
          mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
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

  @Nested
  class OpenAiCompatibleChatModelFactoryTest {

    private static final String API_KEY = "compatibleApiKey";
    private static final String ENDPOINT = "https://compatible.local/v1";
    private static final String MODEL = "some-compatible-model";

    private static final OpenAiCompatibleModelParameters DEFAULT_MODEL_PARAMETERS =
        new OpenAiCompatibleModelParameters(10, 1.0, 0.8, Map.of("my-param", "my-value"));

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
            verify(builder, never()).customHeaders(any());
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

    private void testOpenAiCompatibleChatModelBuilder(
        OpenAiCompatibleProviderConfiguration providerConfig,
        ThrowingConsumer<OpenAiChatModelBuilder> builderAssertions) {
      final var chatModelBuilder = spy(OpenAiChatModel.builder());
      final var chatModelResultCaptor = new ResultCaptor<OpenAiChatModel>();
      doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

      try (MockedStatic<OpenAiChatModel> chatModelMock =
          mockStatic(OpenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
        chatModelMock.when(OpenAiChatModel::builder).thenReturn(chatModelBuilder);

        final var chatModel = chatModelFactory.createChatModel(providerConfig);
        assertThat(chatModel).isNotNull().isInstanceOf(OpenAiChatModel.class);
        assertThat(chatModel).isSameAs(chatModelResultCaptor.getResult());

        builderAssertions.accept(chatModelBuilder);
      }
    }

    static Stream<OpenAiCompatibleModelParameters> nullModelParameters() {
      return Stream.of(new OpenAiCompatibleModelParameters(null, null, null, null));
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
