/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import static io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication.*;
import static io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.VERTEX_AI_DEFAULT_PUBLISHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import io.camunda.connector.fixture.EmbeddingModelProviderFixture;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

class DefaultEmbeddingModelFactoryTest {

  private final DefaultEmbeddingModelFactory factory = new DefaultEmbeddingModelFactory();

  @Nested
  class BedrockEmbeddingModelTests {

    @Test
    void createBedrockEmbeddingModelWithoutProxyConfigured() {
      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.empty());

      var factoryWithMockedProxy = new DefaultEmbeddingModelFactory(proxyConfig);
      var bedrockBuilder = spy(BedrockTitanEmbeddingModel.builder());

      try (var modelMock =
          mockStatic(BedrockTitanEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(BedrockTitanEmbeddingModel::builder).thenReturn(bedrockBuilder);

        var model =
            factoryWithMockedProxy.createEmbeddingModel(
                EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel());

        assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(bedrockBuilder, never()).client(any());
      }
    }

    @Test
    void configureBedrockProxyWithoutCredentials() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails("https", "proxy.example.com", 8080, null, null);

      testBedrockProxyConfiguration(
          proxyDetails,
          awsProxyConfigBuilder -> {
            verify(awsProxyConfigBuilder).scheme("https");
            verify(awsProxyConfigBuilder).endpoint(URI.create("https://proxy.example.com:8080"));
            verify(awsProxyConfigBuilder).nonProxyHosts(any());
            verify(awsProxyConfigBuilder).build();
          });
    }

    @Test
    void configureBedrockProxyWithCredentials() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails(
              "https", "proxy.example.com", 8080, "proxyuser", "proxypass");

      testBedrockProxyConfiguration(
          proxyDetails,
          awsProxyConfigBuilder -> {
            verify(awsProxyConfigBuilder).scheme("https");
            verify(awsProxyConfigBuilder).endpoint(URI.create("https://proxy.example.com:8080"));
            verify(awsProxyConfigBuilder).nonProxyHosts(any());
            verify(awsProxyConfigBuilder).username("proxyuser");
            verify(awsProxyConfigBuilder).password("proxypass");
            verify(awsProxyConfigBuilder).build();
          });
    }

    @SuppressWarnings("resource")
    private void testBedrockProxyConfiguration(
        ProxyConfiguration.ProxyDetails proxyDetails,
        ThrowingConsumer<software.amazon.awssdk.http.apache.ProxyConfiguration.Builder>
            awsProxyConfigVerifications) {

      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.of(proxyDetails));
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTP))
          .thenReturn(Optional.empty());

      var factoryWithMockedProxy = new DefaultEmbeddingModelFactory(proxyConfig);

      var awsProxyConfigBuilder =
          spy(software.amazon.awssdk.http.apache.ProxyConfiguration.builder());
      var mockAwsProxyConfig = mock(software.amazon.awssdk.http.apache.ProxyConfiguration.class);
      when(awsProxyConfigBuilder.build()).thenReturn(mockAwsProxyConfig);

      var apacheHttpClientBuilder = mock(ApacheHttpClient.Builder.class);
      var mockSdkHttpClient = mock(SdkHttpClient.class);
      when(apacheHttpClientBuilder.proxyConfiguration(any())).thenReturn(apacheHttpClientBuilder);
      when(apacheHttpClientBuilder.build()).thenReturn(mockSdkHttpClient);

      var bedrockRuntimeClientBuilder = mock(BedrockRuntimeClientBuilder.class);
      var mockBedrockClient = mock(BedrockRuntimeClient.class);
      when(bedrockRuntimeClientBuilder.httpClient(any())).thenReturn(bedrockRuntimeClientBuilder);
      when(bedrockRuntimeClientBuilder.build()).thenReturn(mockBedrockClient);

      var bedrockTitanBuilder = spy(BedrockTitanEmbeddingModel.builder());

      try (var awsProxyConfigMock =
              mockStatic(
                  software.amazon.awssdk.http.apache.ProxyConfiguration.class,
                  Answers.CALLS_REAL_METHODS);
          var apacheHttpClientMock = mockStatic(ApacheHttpClient.class);
          var bedrockRuntimeClientMock = mockStatic(BedrockRuntimeClient.class);
          var bedrockTitanMock =
              mockStatic(BedrockTitanEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {

        awsProxyConfigMock
            .when(software.amazon.awssdk.http.apache.ProxyConfiguration::builder)
            .thenReturn(awsProxyConfigBuilder);
        apacheHttpClientMock.when(ApacheHttpClient::builder).thenReturn(apacheHttpClientBuilder);
        bedrockRuntimeClientMock
            .when(BedrockRuntimeClient::builder)
            .thenReturn(bedrockRuntimeClientBuilder);
        bedrockTitanMock.when(BedrockTitanEmbeddingModel::builder).thenReturn(bedrockTitanBuilder);

        var model =
            factoryWithMockedProxy.createEmbeddingModel(
                EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel());

        assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
        verify(proxyConfig, times(1)).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);

        awsProxyConfigVerifications.accept(awsProxyConfigBuilder);

        verify(apacheHttpClientBuilder).proxyConfiguration(mockAwsProxyConfig);
        verify(apacheHttpClientBuilder).build();
        verify(bedrockRuntimeClientBuilder).httpClient(mockSdkHttpClient);
        verify(bedrockRuntimeClientBuilder).build();
        verify(bedrockTitanBuilder).client(mockBedrockClient);
      }
    }
  }

  @Nested
  class OpenAiEmbeddingModelTests {

    private static final String OPEN_AI_API_KEY = "test-api-key";
    private static final String OPEN_AI_MODEL_NAME = "test-model-name";

    @Test
    void createOpenAiEmbeddingModelWithAllParameters() {
      OpenAiEmbeddingModelProvider provider =
          new OpenAiEmbeddingModelProvider(
              new OpenAiEmbeddingModelProvider.Configuration(
                  OPEN_AI_API_KEY,
                  OPEN_AI_MODEL_NAME,
                  "test-org-id",
                  "test-project-id",
                  1536,
                  5,
                  Map.of("X-Test-Header", "value"),
                  "https://custom.openai.com/v1/"));

      testOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).organizationId(provider.openAi().organizationId());
            verify(builder).projectId(provider.openAi().projectId());
            verify(builder).dimensions(provider.openAi().dimensions());
            verify(builder).maxRetries(provider.openAi().maxRetries());
            verify(builder).customHeaders(provider.openAi().customHeaders());
            verify(builder).baseUrl(provider.openAi().baseUrl());
          });
    }

    @Test
    void createOpenAiEmbeddingModelWithoutOptionalParameters() {
      OpenAiEmbeddingModelProvider provider =
          new OpenAiEmbeddingModelProvider(
              new OpenAiEmbeddingModelProvider.Configuration(
                  OPEN_AI_API_KEY, OPEN_AI_MODEL_NAME, null, null, null, null, null, null));

      testOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder, never()).organizationId(anyString());
            verify(builder, never()).projectId(anyString());
            verify(builder, never()).dimensions(anyInt());
            verify(builder, never()).maxRetries(anyInt());
            verify(builder, never()).customHeaders(anyMap());
            verify(builder, never()).baseUrl(anyString());
          });
    }

    @Test
    void configureOpenAiProxy() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails(
              "http", "proxy.example.com", 8080, "proxyuser", "proxypass");

      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTP))
          .thenReturn(Optional.of(proxyDetails));

      var factoryWithMockedProxy = new DefaultEmbeddingModelFactory(proxyConfig);

      var provider =
          new OpenAiEmbeddingModelProvider(
              new OpenAiEmbeddingModelProvider.Configuration(
                  OPEN_AI_API_KEY, OPEN_AI_MODEL_NAME, null, null, null, null, null, null));

      var builder = spy(OpenAiEmbeddingModel.builder());

      try (var modelMock = mockStatic(OpenAiEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(OpenAiEmbeddingModel::builder).thenReturn(builder);

        var model = factoryWithMockedProxy.createEmbeddingModel(provider);

        assertThat(model).isInstanceOf(OpenAiEmbeddingModel.class);
        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);
        verify(builder).httpClientBuilder(any());
      }
    }

    private void testOpenAiEmbeddingModelBuilder(
        OpenAiEmbeddingModelProvider provider,
        ThrowingConsumer<OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder> builderAssertions) {
      var builder = spy(OpenAiEmbeddingModel.builder());
      try (var modelMock = mockStatic(OpenAiEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(OpenAiEmbeddingModel::builder).thenReturn(builder);

        EmbeddingModel embeddingModel = factory.createEmbeddingModel(provider);
        assertThat(embeddingModel).isInstanceOf(OpenAiEmbeddingModel.class);

        verify(builder).apiKey(OPEN_AI_API_KEY);
        verify(builder).modelName(OPEN_AI_MODEL_NAME);
        builderAssertions.accept(builder);
      }
    }
  }

  @Nested
  class AzureOpenAiEmbeddingModelTests {

    private static final String AZURE_OPENAI_API_KEY = "azureOpenAiApiKey";
    private static final String AZURE_OPENAI_ENDPOINT = "https://azure-openai-endpoint.local";
    private static final String AZURE_OPENAI_DEPLOYMENT_NAME = "text-embedding-3-small";

    @Test
    void createAzureOpenAiEmbeddingModelWithAllParameters() {
      AzureOpenAiEmbeddingModelProvider provider =
          new AzureOpenAiEmbeddingModelProvider(
              new AzureOpenAiEmbeddingModelProvider.Configuration(
                  AZURE_OPENAI_ENDPOINT,
                  new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
                  AZURE_OPENAI_DEPLOYMENT_NAME,
                  1536,
                  5,
                  Map.of("X-Test-Header", "value")));

      testAzureOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).dimensions(provider.azureOpenAi().dimensions());
            verify(builder).maxRetries(provider.azureOpenAi().maxRetries());
            verify(builder).customHeaders(provider.azureOpenAi().customHeaders());
          });
    }

    @Test
    void createAzureOpenAiEmbeddingModelWithoutOptionalParameters() {
      AzureOpenAiEmbeddingModelProvider provider =
          new AzureOpenAiEmbeddingModelProvider(
              new AzureOpenAiEmbeddingModelProvider.Configuration(
                  AZURE_OPENAI_ENDPOINT,
                  new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
                  AZURE_OPENAI_DEPLOYMENT_NAME,
                  null,
                  null,
                  null));

      testAzureOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder, never()).dimensions(anyInt());
            verify(builder, never()).maxRetries(anyInt());
            verify(builder, never()).customHeaders(anyMap());
          });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://login.microsoft.com/"})
    void createAzureOpenAiEmbeddingModelWithClientCredentials(String authorityHost) {
      AzureOpenAiEmbeddingModelProvider provider =
          new AzureOpenAiEmbeddingModelProvider(
              new AzureOpenAiEmbeddingModelProvider.Configuration(
                  AZURE_OPENAI_ENDPOINT,
                  new AzureClientCredentialsAuthentication(
                      "client-id", "client-secret", "tenant-id", authorityHost),
                  AZURE_OPENAI_DEPLOYMENT_NAME,
                  1536,
                  5,
                  Map.of("X-Test-Header", "value")));

      testAzureOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).dimensions(provider.azureOpenAi().dimensions());
            verify(builder).maxRetries(provider.azureOpenAi().maxRetries());
            verify(builder).customHeaders(provider.azureOpenAi().customHeaders());
          });
    }

    @Test
    void configureAzureOpenAiProxy() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails(
              "https", "proxy.example.com", 8080, "proxyuser", "proxypass");

      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.of(proxyDetails));
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTP))
          .thenReturn(Optional.empty());

      var factoryWithMockedProxy = new DefaultEmbeddingModelFactory(proxyConfig);

      var provider =
          new AzureOpenAiEmbeddingModelProvider(
              new AzureOpenAiEmbeddingModelProvider.Configuration(
                  AZURE_OPENAI_ENDPOINT,
                  new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
                  AZURE_OPENAI_DEPLOYMENT_NAME,
                  null,
                  null,
                  null));

      var builder = spy(AzureOpenAiEmbeddingModel.builder());

      try (var modelMock =
          mockStatic(AzureOpenAiEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(AzureOpenAiEmbeddingModel::builder).thenReturn(builder);

        var model = factoryWithMockedProxy.createEmbeddingModel(provider);

        assertThat(model).isInstanceOf(AzureOpenAiEmbeddingModel.class);
        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);
        verify(builder).proxyOptions(any());
      }
    }

    private void testAzureOpenAiEmbeddingModelBuilder(
        AzureOpenAiEmbeddingModelProvider provider,
        ThrowingConsumer<AzureOpenAiEmbeddingModel.Builder> builderAssertions) {
      var builder = spy(AzureOpenAiEmbeddingModel.builder());
      try (var modelMock =
          mockStatic(AzureOpenAiEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(AzureOpenAiEmbeddingModel::builder).thenReturn(builder);

        EmbeddingModel embeddingModel = factory.createEmbeddingModel(provider);
        assertThat(embeddingModel).isInstanceOf(AzureOpenAiEmbeddingModel.class);

        verify(builder).endpoint(AZURE_OPENAI_ENDPOINT);
        verify(builder).deploymentName(AZURE_OPENAI_DEPLOYMENT_NAME);
        verify(builder, never()).proxyOptions(any());

        switch (provider.azureOpenAi().authentication()) {
          case AzureAuthentication.AzureApiKeyAuthentication(String apiKey) -> {
            verify(builder).apiKey(apiKey);
            verify(builder, never()).tokenCredential(any(TokenCredential.class));
          }
          case AzureClientCredentialsAuthentication ignored -> {
            verify(builder).tokenCredential(any(TokenCredential.class));
            verify(builder, never()).apiKey(anyString());
          }
        }

        builderAssertions.accept(builder);
      }
    }
  }

  @Nested
  class GoogleVertexAiEmbeddingModelTests {
    private static final String PROJECT_ID = "test-project-id";
    private static final String REGION = "us-central1";
    private static final String MODEL_NAME = "test-model";
    private static final Integer DIMENSIONS = 768;
    private static final String PUBLISHER = "publisher-name";
    private static final Integer MAX_RETRIES = 5;

    @Test
    void createVertexAiEmbeddingModel() {
      var provider =
          new GoogleVertexAiEmbeddingModelProvider(
              new GoogleVertexAiEmbeddingModelProvider.Configuration(
                  PROJECT_ID,
                  REGION,
                  new ApplicationDefaultCredentialsAuthentication(),
                  MODEL_NAME,
                  DIMENSIONS,
                  PUBLISHER,
                  MAX_RETRIES));
      testVertexAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).publisher(PUBLISHER);
            verify(builder).outputDimensionality(DIMENSIONS);
            verify(builder).maxRetries(MAX_RETRIES);
            verify(builder, never()).credentials(any());
          });
    }

    @Test
    void createVertexAiEmbeddingModelWithServiceAccountCredentials() {
      var provider =
          new GoogleVertexAiEmbeddingModelProvider(
              new GoogleVertexAiEmbeddingModelProvider.Configuration(
                  PROJECT_ID,
                  REGION,
                  new ServiceAccountCredentialsAuthentication("{}"),
                  MODEL_NAME,
                  DIMENSIONS,
                  PUBLISHER,
                  MAX_RETRIES));
      try (final var staticMockedSac = mockStatic(ServiceAccountCredentials.class)) {
        final var mockedSac = mock(ServiceAccountCredentials.class);
        when(mockedSac.createScoped(anyString())).thenReturn(mockedSac);
        staticMockedSac
            .when(() -> ServiceAccountCredentials.fromStream(any()))
            .thenReturn(mockedSac);

        testVertexAiEmbeddingModelBuilder(
            provider,
            (builder) -> {
              verify(builder).publisher(PUBLISHER);
              verify(builder).outputDimensionality(DIMENSIONS);
              verify(builder).maxRetries(MAX_RETRIES);
              verify(builder).credentials(mockedSac);
            });

        staticMockedSac.verify(() -> ServiceAccountCredentials.fromStream(any()));
      }
    }

    @Test
    void createVertexAiEmbeddingModelWithMandatoryParametersOnly() {
      var provider =
          new GoogleVertexAiEmbeddingModelProvider(
              new GoogleVertexAiEmbeddingModelProvider.Configuration(
                  PROJECT_ID,
                  REGION,
                  new ApplicationDefaultCredentialsAuthentication(),
                  MODEL_NAME,
                  DIMENSIONS,
                  null,
                  null));

      testVertexAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).publisher(VERTEX_AI_DEFAULT_PUBLISHER);
            verify(builder, never()).maxRetries(anyInt());
          });
    }

    private void testVertexAiEmbeddingModelBuilder(
        GoogleVertexAiEmbeddingModelProvider provider,
        ThrowingConsumer<VertexAiEmbeddingModel.Builder> builderAssertions) {
      var builder = spy(VertexAiEmbeddingModel.builder());
      try (var modelMock = mockStatic(VertexAiEmbeddingModel.class, Answers.CALLS_REAL_METHODS)) {
        modelMock.when(VertexAiEmbeddingModel::builder).thenReturn(builder);
        EmbeddingModel embeddingModel = factory.createEmbeddingModel(provider);
        assertThat(embeddingModel).isInstanceOf(VertexAiEmbeddingModel.class);

        verify(builder).project(PROJECT_ID);
        verify(builder).location(REGION);
        verify(builder).modelName(MODEL_NAME);
        verify(builder).outputDimensionality(DIMENSIONS);

        builderAssertions.accept(builder);
      }
    }
  }
}
