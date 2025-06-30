/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultEmbeddingStoreFactoryTest {

  @Test
  void createsElasticSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);
    final var factory = new DefaultEmbeddingStoreFactory();

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createElasticSearchVectorStore(), mockModel);
    Assertions.assertThat(store).isInstanceOf(ElasticsearchEmbeddingStore.class);
  }

  @Test
  void createsOpenSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);
    final var factory = new DefaultEmbeddingStoreFactory();

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(), mockModel);
    Assertions.assertThat(store).isInstanceOf(OpenSearchEmbeddingStore.class);
  }

  @Test
  void createsAmazonManagedOpenSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);
    final var factory = new DefaultEmbeddingStoreFactory();

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(), mockModel);
    Assertions.assertThat(store).isInstanceOf(OpenSearchEmbeddingStore.class);
  }
}
