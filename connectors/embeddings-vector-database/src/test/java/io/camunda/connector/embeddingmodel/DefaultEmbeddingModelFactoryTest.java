/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.camunda.connector.fixture.EmbeddingModelProviderFixture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultEmbeddingModelFactoryTest {

  @Test
  void createBedrockEmbeddingModel() {
    final var factory = new DefaultEmbeddingModelFactory();

    final var model =
        factory.initializeModel(EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel());

    Assertions.assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
  }

  @Test
  void createOllamaEmbeddingModel() {
    final var factory = new DefaultEmbeddingModelFactory();

    final var model =
        factory.initializeModel(EmbeddingModelProviderFixture.createDefaultOllamaEmbeddingModel());

    Assertions.assertThat(model).isInstanceOf(OllamaEmbeddingModel.class);
  }
}
