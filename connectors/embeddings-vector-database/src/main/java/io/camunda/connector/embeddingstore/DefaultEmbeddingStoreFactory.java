/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

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
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class DefaultEmbeddingStoreFactory {

  public static final String AZURE_COSMOS_DB_VECTOR_EMBEDDING_PATH = "/embedding";
  public static final String AZURE_COSMOS_DB_PARTITION_KEY_PATH = "/id";

  public EmbeddingStore<TextSegment> initializeVectorStore(
      EmbeddingsVectorStore embeddingsVectorStore, EmbeddingModel model) {
    return switch (embeddingsVectorStore) {
      case ElasticSearchVectorStore elasticSearchVectorStore ->
          initializeElasticSearchVectorStore(elasticSearchVectorStore);
      case OpenSearchVectorStore openSearchVectorStore ->
          initializeOpenSearchVectorStore(openSearchVectorStore);
      case AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore ->
          initializeAmazonManagedOpenSearchVectorStore(amazonManagedOpenSearchVectorStore);
      case AzureCosmosDbNoSqlVectorStore azureCosmosDbNoSqlVectorStore ->
          initializeAzureCosmosDbNoSqlVectorStore(azureCosmosDbNoSqlVectorStore, model);
    };
  }

  private EmbeddingStore<TextSegment> initializeAzureCosmosDbNoSqlVectorStore(
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
    embedding.setPath(AZURE_COSMOS_DB_VECTOR_EMBEDDING_PATH);
    embedding.setDataType(CosmosVectorDataType.FLOAT32);
    embedding.setEmbeddingDimensions(model.dimension());
    embedding.setDistanceFunction(
        mapDistanceFunction(azureCosmosDbNoSqlVectorStore.distanceFunction()));
    final var embeddingPolicy = new CosmosVectorEmbeddingPolicy();
    embeddingPolicy.setCosmosVectorEmbeddings(List.of(embedding));

    var vectorIndexSpec = new CosmosVectorIndexSpec();
    vectorIndexSpec.setPath(AZURE_COSMOS_DB_VECTOR_EMBEDDING_PATH);
    vectorIndexSpec.setType(
        mapIndexType(azureCosmosDbNoSqlVectorStore.vectorIndexType()).toString());

    final var partitionKeyDef = new PartitionKeyDefinition();
    partitionKeyDef.setPaths(List.of(AZURE_COSMOS_DB_PARTITION_KEY_PATH));

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
      AzureCosmosDbNoSqlVectorStore.CosmosConsistencyLevel consistencyLevel) {
    return switch (consistencyLevel) {
      case STRONG -> ConsistencyLevel.STRONG;
      case BOUNDED_STALENESS -> ConsistencyLevel.BOUNDED_STALENESS;
      case SESSION -> ConsistencyLevel.SESSION;
      case CONSISTENT_PREFIX -> ConsistencyLevel.CONSISTENT_PREFIX;
      case EVENTUAL -> ConsistencyLevel.EVENTUAL;
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

  private EmbeddingStore<TextSegment> initializeElasticSearchVectorStore(
      ElasticSearchVectorStore elasticSearchVectorStore) {
    RestClientBuilder restClientBuilder =
        RestClient.builder(HttpHost.create(elasticSearchVectorStore.baseUrl()));

    if (!isNullOrBlank(elasticSearchVectorStore.userName())) {
      CredentialsProvider provider = new BasicCredentialsProvider();
      provider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(
              elasticSearchVectorStore.userName(), elasticSearchVectorStore.password()));
      restClientBuilder.setHttpClientConfigCallback(
          httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(provider));
    }

    return ElasticsearchEmbeddingStore.builder()
        .restClient(restClientBuilder.build())
        .indexName(elasticSearchVectorStore.indexName())
        .build();
  }

  private EmbeddingStore<TextSegment> initializeOpenSearchVectorStore(
      OpenSearchVectorStore openSearchVectorStore) {
    return OpenSearchEmbeddingStore.builder()
        .serverUrl(openSearchVectorStore.baseUrl())
        .userName(openSearchVectorStore.userName())
        .password(openSearchVectorStore.password())
        .indexName(openSearchVectorStore.indexName())
        .build();
  }

  private EmbeddingStore<TextSegment> initializeAmazonManagedOpenSearchVectorStore(
      AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore) {
    return OpenSearchEmbeddingStore.builder()
        .serviceName("es") // for managed AWS OS
        .serverUrl(amazonManagedOpenSearchVectorStore.serverUrl())
        .region(amazonManagedOpenSearchVectorStore.region())
        .options(
            AwsSdk2TransportOptions.builder()
                .setCredentials(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            amazonManagedOpenSearchVectorStore.accessKey(),
                            amazonManagedOpenSearchVectorStore.secretKey())))
                .build())
        .indexName(amazonManagedOpenSearchVectorStore.indexName())
        .build();
  }
}
