/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.connector.fixture.EmbeddingModelProviderFixture;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import java.util.Map;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class DefaultEmbeddingModelFactoryTest {

  private final DefaultEmbeddingModelFactory factory = new DefaultEmbeddingModelFactory();

  @Nested
  class BedrockEmbeddingModelFactoryTest {

    @Test
    void createBedrockEmbeddingModel() {
      final var model =
          factory.createEmbeddingModel(
              EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel());

      assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
    }
  }

  @Nested
  class OpenAiEmbeddingModelFactoryTest {

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
}
