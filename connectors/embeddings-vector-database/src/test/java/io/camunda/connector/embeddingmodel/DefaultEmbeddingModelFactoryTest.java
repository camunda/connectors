/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import static io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.azure.core.credential.TokenCredential;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.connector.fixture.EmbeddingModelProviderFixture;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import java.util.Map;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

class DefaultEmbeddingModelFactoryTest {

  private final DefaultEmbeddingModelFactory factory = new DefaultEmbeddingModelFactory();

  @Nested
  class BedrockEmbeddingModelTests {

    @Test
    void createBedrockEmbeddingModel() {
      final var model =
          factory.createEmbeddingModel(
              EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel());

      assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
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
              OPEN_AI_API_KEY,
              OPEN_AI_MODEL_NAME,
              "test-org-id",
              "test-project-id",
              1536,
              5,
              Map.of("X-Test-Header", "value"),
              "https://custom.openai.com/v1/");

      testOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).organizationId(provider.organizationId());
            verify(builder).projectId(provider.projectId());
            verify(builder).dimensions(provider.dimensions());
            verify(builder).maxRetries(provider.maxRetries());
            verify(builder).customHeaders(provider.customHeaders());
            verify(builder).baseUrl(provider.baseUrl());
          });
    }

    @Test
    void createOpenAiEmbeddingModelWithoutOptionalParameters() {
      OpenAiEmbeddingModelProvider provider =
          new OpenAiEmbeddingModelProvider(
              OPEN_AI_API_KEY, OPEN_AI_MODEL_NAME, null, null, null, null, null, null);

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
    private static final String AZURE_OPENAI_ENDPOINT = "azure-openai-endpoint.local";
    private static final String AZURE_OPENAI_DEPLOYMENT_NAME = "text-embedding-3-small";

    @Test
    void createAzureOpenAiEmbeddingModelWithAllParameters() {
      AzureOpenAiEmbeddingModelProvider provider =
          new AzureOpenAiEmbeddingModelProvider(
              AZURE_OPENAI_ENDPOINT,
              new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
              AZURE_OPENAI_DEPLOYMENT_NAME,
              1536,
              5,
              Map.of("X-Test-Header", "value"));

      testAzureOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).dimensions(provider.dimensions());
            verify(builder).maxRetries(provider.maxRetries());
            verify(builder).customHeaders(provider.customHeaders());
          });
    }

    @Test
    void createAzureOpenAiEmbeddingModelWithoutOptionalParameters() {
      AzureOpenAiEmbeddingModelProvider provider =
          new AzureOpenAiEmbeddingModelProvider(
              AZURE_OPENAI_ENDPOINT,
              new AzureApiKeyAuthentication(AZURE_OPENAI_API_KEY),
              AZURE_OPENAI_DEPLOYMENT_NAME,
              null,
              null,
              null);

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
              AZURE_OPENAI_ENDPOINT,
              new AzureClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", authorityHost),
              AZURE_OPENAI_DEPLOYMENT_NAME,
              1536,
              5,
              Map.of("X-Test-Header", "value"));

      testAzureOpenAiEmbeddingModelBuilder(
          provider,
          (builder) -> {
            verify(builder).dimensions(provider.dimensions());
            verify(builder).maxRetries(provider.maxRetries());
            verify(builder).customHeaders(provider.customHeaders());
          });
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

        switch (provider.authentication()) {
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
}
