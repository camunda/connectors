/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbedding;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.identity.ClientSecretCredentialBuilder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDbNoSqlEmbeddingStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class AzureCosmosDbNoSqlVectorStoreFactory {

  public static final String VECTOR_EMBEDDING_PATH = "/embedding";
  public static final String PARTITION_KEY_PATH = "/id";

  public EmbeddingStore<TextSegment> createEmbeddingStore(
      AzureCosmosDbNoSqlVectorStore azureCosmosDbNoSqlVectorStore, EmbeddingModel model) {

    final var cosmosClientBuilder = new CosmosClientBuilder();
    cosmosClientBuilder.endpoint(azureCosmosDbNoSqlVectorStore.endpoint());
    cosmosClientBuilder.consistencyLevel(
        mapConsistencyLevel(azureCosmosDbNoSqlVectorStore.consistencyLevel()));
    cosmosClientBuilder.contentResponseOnWriteEnabled(true);

    switch (azureCosmosDbNoSqlVectorStore.authentication()) {
      case AzureCosmosDbNoSqlVectorStore.AzureAuthentication.AzureApiKeyAuthentication apiKey ->
          cosmosClientBuilder.key(apiKey.apiKey());
      case AzureCosmosDbNoSqlVectorStore.AzureAuthentication.AzureClientCredentialsAuthentication
              auth -> {
        ClientSecretCredentialBuilder clientSecretCredentialBuilder =
            new ClientSecretCredentialBuilder()
                .clientId(auth.clientId())
                .clientSecret(auth.clientSecret())
                .tenantId(auth.tenantId());
        if (StringUtils.isNotBlank(auth.authorityHost())) {
          clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
        }
        cosmosClientBuilder.credential(clientSecretCredentialBuilder.build());
      }
    }

    final var embedding = new CosmosVectorEmbedding();
    embedding.setPath(VECTOR_EMBEDDING_PATH);
    embedding.setDataType(CosmosVectorDataType.FLOAT32);
    embedding.setEmbeddingDimensions(model.dimension());
    embedding.setDistanceFunction(
        mapDistanceFunction(azureCosmosDbNoSqlVectorStore.distanceFunction()));
    final var embeddingPolicy = new CosmosVectorEmbeddingPolicy();
    embeddingPolicy.setCosmosVectorEmbeddings(List.of(embedding));

    final var vectorIndexSpec = new CosmosVectorIndexSpec();
    vectorIndexSpec.setPath(VECTOR_EMBEDDING_PATH);
    vectorIndexSpec.setType(
        mapIndexType(azureCosmosDbNoSqlVectorStore.vectorIndexType()).toString());

    final var partitionKeyDef = new PartitionKeyDefinition();
    partitionKeyDef.setPaths(List.of(PARTITION_KEY_PATH));

    final var containerProperties =
        new CosmosContainerProperties(
            azureCosmosDbNoSqlVectorStore.containerName(), partitionKeyDef);
    final var indexingPolicy = new IndexingPolicy();
    indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
    indexingPolicy.setIncludedPaths(List.of(new IncludedPath("/*")));
    containerProperties.setIndexingPolicy(indexingPolicy);

    return AzureCosmosDbNoSqlEmbeddingStore.builder()
        .cosmosClient(cosmosClientBuilder.buildClient())
        .databaseName(azureCosmosDbNoSqlVectorStore.databaseName())
        .containerName(azureCosmosDbNoSqlVectorStore.containerName())
        .cosmosVectorEmbeddingPolicy(embeddingPolicy)
        .cosmosVectorIndexes(List.of(vectorIndexSpec))
        .containerProperties(containerProperties)
        .build();
  }

  private ConsistencyLevel mapConsistencyLevel(
      AzureCosmosDbNoSqlVectorStore.ConsistencyLevel consistencyLevel) {
    return switch (consistencyLevel) {
      case STRONG -> com.azure.cosmos.ConsistencyLevel.STRONG;
      case BOUNDED_STALENESS -> com.azure.cosmos.ConsistencyLevel.BOUNDED_STALENESS;
      case SESSION -> com.azure.cosmos.ConsistencyLevel.SESSION;
      case CONSISTENT_PREFIX -> com.azure.cosmos.ConsistencyLevel.CONSISTENT_PREFIX;
      case EVENTUAL -> com.azure.cosmos.ConsistencyLevel.EVENTUAL;
    };
  }

  private CosmosVectorDistanceFunction mapDistanceFunction(
      AzureCosmosDbNoSqlVectorStore.DistanceFunction distanceFunction) {
    return switch (distanceFunction) {
      case EUCLIDEAN -> CosmosVectorDistanceFunction.EUCLIDEAN;
      case COSINE -> CosmosVectorDistanceFunction.COSINE;
      case DOT_PRODUCT -> CosmosVectorDistanceFunction.DOT_PRODUCT;
    };
  }

  private CosmosVectorIndexType mapIndexType(AzureCosmosDbNoSqlVectorStore.IndexType indexType) {
    return switch (indexType) {
      case FLAT -> CosmosVectorIndexType.FLAT;
      case QUANTIZED_FLAT -> CosmosVectorIndexType.QUANTIZED_FLAT;
      case DISK_ANN -> CosmosVectorIndexType.DISK_ANN;
    };
  }
}
