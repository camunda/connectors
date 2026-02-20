/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDbNoSqlEmbeddingStore;
import dev.langchain4j.store.embedding.azure.search.AzureAiSearchEmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultEmbeddingStoreFactoryTest {

  @Test
  void createsElasticsearchVectorStore() {
    DefaultEmbeddingStoreFactory factory =
        new DefaultEmbeddingStoreFactory(new ProxyConfiguration());
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createElasticsearchVectorStore(), mockModel, null);
    assertThat(store.getEmbeddingStore()).isInstanceOf(ElasticsearchEmbeddingStore.class);
  }

  @Test
  void createsOpenSearchVectorStore() {
    DefaultEmbeddingStoreFactory factory =
        new DefaultEmbeddingStoreFactory(new ProxyConfiguration());
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(), mockModel, null);
    assertThat(store.getEmbeddingStore()).isInstanceOf(OpenSearchEmbeddingStore.class);
  }

  @Test
  void createsAmazonManagedOpenSearchVectorStore() {
    DefaultEmbeddingStoreFactory factory =
        new DefaultEmbeddingStoreFactory(new ProxyConfiguration());
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(), mockModel, null);
    assertThat(store.getEmbeddingStore()).isInstanceOf(OpenSearchEmbeddingStore.class);
  }

  @Test
  void createsAzureCosmosDbNoSqlVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);
    Mockito.when(mockModel.dimension()).thenReturn(512);

    AzureCosmosDbNoSqlEmbeddingStore mockEmbeddingStore =
        mock(AzureCosmosDbNoSqlEmbeddingStore.class);

    try (var mockedConstruction =
        mockConstruction(
            AzureVectorStoreFactory.class,
            (mock, context) -> {
              when(mock.createCosmosDbNoSqlVectorStore(
                      any(AzureCosmosDbNoSqlVectorStore.class), any(EmbeddingModel.class)))
                  .thenReturn(ClosableEmbeddingStore.wrap(mockEmbeddingStore));
            })) {

      DefaultEmbeddingStoreFactory factory =
          new DefaultEmbeddingStoreFactory(new ProxyConfiguration());

      final var store =
          factory.initializeVectorStore(
              EmbeddingsVectorStoreFixture.createAzureCosmosDbNoSqlVectorStore(), mockModel, null);

      assertThat(store.getEmbeddingStore()).isInstanceOf(AzureCosmosDbNoSqlEmbeddingStore.class);
      assertThat(mockedConstruction.constructed()).hasSize(1);
      verify(mockedConstruction.constructed().getFirst())
          .createCosmosDbNoSqlVectorStore(any(AzureCosmosDbNoSqlVectorStore.class), eq(mockModel));
    }
  }

  @Test
  void createsAzureAiSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);
    Mockito.when(mockModel.dimension()).thenReturn(512);

    AzureAiSearchEmbeddingStore mockEmbeddingStore = mock(AzureAiSearchEmbeddingStore.class);

    try (var mockedConstruction =
        mockConstruction(
            AzureVectorStoreFactory.class,
            (mock, context) -> {
              when(mock.createAiSearchVectorStore(
                      any(AzureAiSearchVectorStore.class), any(EmbeddingModel.class), any()))
                  .thenReturn(ClosableEmbeddingStore.wrap(mockEmbeddingStore));
            })) {

      DefaultEmbeddingStoreFactory factory =
          new DefaultEmbeddingStoreFactory(new ProxyConfiguration());

      final var store =
          factory.initializeVectorStore(
              EmbeddingsVectorStoreFixture.createAzureAiSearchVectorStore(), mockModel, null);

      assertThat(store.getEmbeddingStore()).isInstanceOf(AzureAiSearchEmbeddingStore.class);
      assertThat(mockedConstruction.constructed()).hasSize(1);
      verify(mockedConstruction.constructed().getFirst())
          .createAiSearchVectorStore(any(AzureAiSearchVectorStore.class), eq(mockModel), any());
    }
  }
}
