/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDbNoSqlEmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore.AzureAuthentication.AzureClientCredentialsAuthentication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DefaultEmbeddingStoreFactoryTest {

  DefaultEmbeddingStoreFactory factory = new DefaultEmbeddingStoreFactory();

  @Test
  void createsElasticSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createElasticSearchVectorStore(), mockModel);
    assertThat(store).isInstanceOf(ElasticsearchEmbeddingStore.class);
  }

  @Test
  void createsOpenSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(), mockModel);
    assertThat(store).isInstanceOf(OpenSearchEmbeddingStore.class);
  }

  @Test
  void createsAmazonManagedOpenSearchVectorStore() {
    final var mockModel = Mockito.mock(EmbeddingModel.class);

    final var store =
        factory.initializeVectorStore(
            EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(), mockModel);
    assertThat(store).isInstanceOf(OpenSearchEmbeddingStore.class);
  }

  @Nested
  class AzureCosmosDbNoSqlVectorStoreTests {

    @Test
    void createsAzureCosmosDbNoSqlVectorStore() {
      final var mockModel = Mockito.mock(EmbeddingModel.class);
      when(mockModel.dimension()).thenReturn(512);
      final var azureCosmosDbNoSqlVectorStore =
          new AzureCosmosDbNoSqlVectorStore(
              "https://example.documents.azure.com:443/",
              new AzureApiKeyAuthentication("api-key"),
              "database-name",
              "container-name",
              AzureCosmosDbNoSqlVectorStore.ConsistencyLevel.STRONG,
              AzureCosmosDbNoSqlVectorStore.DistanceFunction.COSINE,
              AzureCosmosDbNoSqlVectorStore.IndexType.FLAT);

      testAzureCosmosDbNoSqlEmbeddingStoreCreation(azureCosmosDbNoSqlVectorStore, mockModel);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://login.microsoft.com/"})
    void createsAzureCosmosDbNoSqlVectorStoreWithClientCredentials(String authorityHost) {
      final var mockModel = Mockito.mock(EmbeddingModel.class);
      when(mockModel.dimension()).thenReturn(512);
      final var azureCosmosDbNoSqlVectorStore =
          new AzureCosmosDbNoSqlVectorStore(
              "https://example.documents.azure.com:443/",
              new AzureClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", authorityHost),
              "database-name",
              "container-name",
              AzureCosmosDbNoSqlVectorStore.ConsistencyLevel.STRONG,
              AzureCosmosDbNoSqlVectorStore.DistanceFunction.COSINE,
              AzureCosmosDbNoSqlVectorStore.IndexType.FLAT);

      testAzureCosmosDbNoSqlEmbeddingStoreCreation(azureCosmosDbNoSqlVectorStore, mockModel);
    }

    private void testAzureCosmosDbNoSqlEmbeddingStoreCreation(
        AzureCosmosDbNoSqlVectorStore vectorStore, EmbeddingModel mockModel) {
      var builder = spy(AzureCosmosDbNoSqlEmbeddingStore.builder());
      try (var mockedConstruction = Mockito.mockConstruction(CosmosClientBuilder.class)) {
        try (var mockStore =
            Mockito.mockStatic(
                AzureCosmosDbNoSqlEmbeddingStore.class, Answers.CALLS_REAL_METHODS)) {
          mockStore.when(AzureCosmosDbNoSqlEmbeddingStore::builder).thenReturn(builder);

          doReturn(mock(AzureCosmosDbNoSqlEmbeddingStore.class)).when(builder).build();
          ArgumentCaptor<CosmosVectorEmbeddingPolicy> embeddingPolicyCaptor =
              ArgumentCaptor.forClass(CosmosVectorEmbeddingPolicy.class);

          factory.initializeVectorStore(vectorStore, mockModel);
          verify(builder).build();

          CosmosClientBuilder cosmosClientBuilder = mockedConstruction.constructed().getFirst();
          verifyCosmosClientBuilder(vectorStore, cosmosClientBuilder);

          verify(builder).cosmosClient(any());
          verify(builder).databaseName(vectorStore.databaseName());
          verify(builder).containerName(vectorStore.containerName());

          verify(builder).cosmosVectorEmbeddingPolicy(embeddingPolicyCaptor.capture());
          assertThat(embeddingPolicyCaptor.getValue().getVectorEmbeddings().size()).isEqualTo(1);
          var embedding = embeddingPolicyCaptor.getValue().getVectorEmbeddings().getFirst();
          assertThat(embedding.getPath())
              .isEqualTo(AzureCosmosDbNoSqlVectorStoreFactory.VECTOR_EMBEDDING_PATH);
          assertThat(embedding.getDataType()).isEqualTo(CosmosVectorDataType.FLOAT32);
          assertThat(embedding.getEmbeddingDimensions()).isEqualTo(mockModel.dimension());
          assertThat(embedding.getDistanceFunction())
              .isEqualTo(CosmosVectorDistanceFunction.COSINE);

          verify(builder)
              .cosmosVectorIndexes(
                  assertArg(
                      (vectorIndexes) -> {
                        assertThat(vectorIndexes).hasSize(1);
                        var vectorIndex = vectorIndexes.getFirst();
                        assertThat(vectorIndex.getPath())
                            .isEqualTo(AzureCosmosDbNoSqlVectorStoreFactory.VECTOR_EMBEDDING_PATH);
                        assertThat(vectorIndex.getType())
                            .isEqualTo(CosmosVectorIndexType.FLAT.toString());
                      }));

          ArgumentCaptor<CosmosContainerProperties> containerPropertiesCaptor =
              ArgumentCaptor.forClass(CosmosContainerProperties.class);
          verify(builder).containerProperties(containerPropertiesCaptor.capture());

          var containerProperties = containerPropertiesCaptor.getValue();
          assertThat(containerProperties.getId()).isEqualTo(vectorStore.containerName());
          assertThat(containerProperties.getPartitionKeyDefinition().getPaths())
              .containsExactly(AzureCosmosDbNoSqlVectorStoreFactory.PARTITION_KEY_PATH);
          assertThat(containerProperties.getIndexingPolicy().getIndexingMode())
              .isEqualTo(IndexingMode.CONSISTENT);
          assertThat(containerProperties.getIndexingPolicy().getIncludedPaths())
              .extracting(IncludedPath::getPath)
              .containsExactly("/*");
        }
      }
    }

    private static void verifyCosmosClientBuilder(
        AzureCosmosDbNoSqlVectorStore vectorStore, CosmosClientBuilder cosmosClientBuilder) {
      verify(cosmosClientBuilder).endpoint(vectorStore.endpoint());
      verify(cosmosClientBuilder).consistencyLevel(com.azure.cosmos.ConsistencyLevel.STRONG);
      verify(cosmosClientBuilder).contentResponseOnWriteEnabled(true);
      switch (vectorStore.authentication()) {
        case AzureApiKeyAuthentication(String apiKey) -> {
          verify(cosmosClientBuilder).key(apiKey);
          verify(cosmosClientBuilder, never()).credential(any(TokenCredential.class));
        }
        case AzureClientCredentialsAuthentication ignored -> {
          verify(cosmosClientBuilder).credential(any(TokenCredential.class));
          verify(cosmosClientBuilder, never()).key(anyString());
        }
      }
    }
  }
}
