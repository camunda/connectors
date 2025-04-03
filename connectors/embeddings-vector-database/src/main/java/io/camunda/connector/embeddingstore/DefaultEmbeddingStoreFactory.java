/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.embedding.vector.store.PgVectorVectorStore;
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

  public EmbeddingStore<TextSegment> initializeVectorStore(
      EmbeddingsVectorStore embeddingsVectorStore, EmbeddingModel model) {
    return switch (embeddingsVectorStore) {
      case ElasticSearchVectorStore elasticSearchVectorStore ->
          initializeElasticSearchVectorStore(elasticSearchVectorStore);
      case PgVectorVectorStore pgVectorVectorStore ->
          initializePGVectorStore(pgVectorVectorStore, model);
      case AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore ->
          initializeAmazonManagedOpenSearch(amazonManagedOpenSearchVectorStore);
    };
  }

  private EmbeddingStore<TextSegment> initializePGVectorStore(
      PgVectorVectorStore pgVectorVectorStore, EmbeddingModel model) {
    final var host = pgVectorVectorStore.baseUrl().split(":")[0];
    final var port = pgVectorVectorStore.baseUrl().split(":")[1];
    return PgVectorEmbeddingStore.builder()
        .host(host)
        .port(Integer.parseInt(port))
        .database(pgVectorVectorStore.databaseName())
        .user(pgVectorVectorStore.userName())
        .password(pgVectorVectorStore.password())
        .table(pgVectorVectorStore.tableName())
        .dimension(model.dimension())
        .build();
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

  private EmbeddingStore<TextSegment> initializeAmazonManagedOpenSearch(
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
