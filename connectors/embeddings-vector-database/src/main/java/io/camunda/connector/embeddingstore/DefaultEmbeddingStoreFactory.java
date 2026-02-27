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
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchRequestFailedException;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import io.camunda.connector.model.operation.VectorDatabaseConnectorOperation;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class DefaultEmbeddingStoreFactory {

  private final AzureVectorStoreFactory azureVectorStoreFactory = new AzureVectorStoreFactory();

  public ClosableEmbeddingStore<TextSegment> initializeVectorStore(
      EmbeddingsVectorStore embeddingsVectorStore,
      EmbeddingModel model,
      VectorDatabaseConnectorOperation operation) {
    return switch (embeddingsVectorStore) {
      case ElasticsearchVectorStore elasticsearchVectorStore ->
          initializeElasticsearchVectorStore(elasticsearchVectorStore);
      case OpenSearchVectorStore openSearchVectorStore ->
          initializeOpenSearchVectorStore(openSearchVectorStore);
      case AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore ->
          initializeAmazonManagedOpenSearchVectorStore(amazonManagedOpenSearchVectorStore);
      case AzureCosmosDbNoSqlVectorStore azureCosmosDbNoSqlVectorStore ->
          azureVectorStoreFactory.createCosmosDbNoSqlVectorStore(
              azureCosmosDbNoSqlVectorStore, model);
      case AzureAiSearchVectorStore azureAiSearchVectorStore ->
          azureVectorStoreFactory.createAiSearchVectorStore(
              azureAiSearchVectorStore, model, operation);
    };
  }

  private ClosableEmbeddingStore<TextSegment> initializeElasticsearchVectorStore(
      ElasticsearchVectorStore elasticsearchVectorStore) {
    final var elasticsearch = elasticsearchVectorStore.elasticsearch();
    RestClientBuilder restClientBuilder =
        RestClient.builder(HttpHost.create(elasticsearch.baseUrl()));

    if (!isNullOrBlank(elasticsearch.userName())) {
      CredentialsProvider provider = new BasicCredentialsProvider();
      provider.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(elasticsearch.userName(), elasticsearch.password()));
      restClientBuilder.setHttpClientConfigCallback(
          httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(provider));
    }

    RestClient restClient = restClientBuilder.build();
    ElasticsearchEmbeddingStore embeddingStore =
        ElasticsearchEmbeddingStore.builder()
            .restClient(restClient)
            .indexName(elasticsearch.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(
        embeddingStore,
        () -> {
          try {
            restClient.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ClosableEmbeddingStore<TextSegment> initializeOpenSearchVectorStore(
      OpenSearchVectorStore openSearchVectorStore) {
    final var openSearch = openSearchVectorStore.openSearch();

    org.apache.hc.core5.http.HttpHost openSearchHost;
    try {
      openSearchHost = org.apache.hc.core5.http.HttpHost.create(openSearch.baseUrl());
    } catch (URISyntaxException se) {
      throw new OpenSearchRequestFailedException("Failed to create HttpHost from server URL", se);
    }

    OpenSearchTransport transport =
        ApacheHttpClient5TransportBuilder.builder(openSearchHost)
            .setMapper(new JacksonJsonpMapper())
            .setHttpClientConfigCallback(
                httpClientBuilder -> {
                  if (!isNullOrBlank(openSearch.userName())
                      && !isNullOrBlank(openSearch.password())) {
                    org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
                        credentialsProvider =
                            new org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider();
                    credentialsProvider.setCredentials(
                        new org.apache.hc.client5.http.auth.AuthScope(openSearchHost),
                        new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(
                            openSearch.userName(), openSearch.password().toCharArray()));
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                  }

                  // We need to explicitly disable content compression for opensearch-client 2.x
                  // and httpclient5 >= 5.6
                  httpClientBuilder.disableContentCompression();

                  httpClientBuilder.setConnectionManager(
                      PoolingAsyncClientConnectionManagerBuilder.create().build());

                  return httpClientBuilder;
                })
            .build();
    final var openSearchEmbeddingStore =
        OpenSearchEmbeddingStore.builder()
            .openSearchClient(new OpenSearchClient(transport))
            .indexName(openSearch.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(
        openSearchEmbeddingStore,
        () -> {
          try {
            transport.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ClosableEmbeddingStore<TextSegment> initializeAmazonManagedOpenSearchVectorStore(
      AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore) {
    final var amazonManagedOpenSearch =
        amazonManagedOpenSearchVectorStore.amazonManagedOpensearch();
    OpenSearchEmbeddingStore openSearchEmbeddingStore =
        OpenSearchEmbeddingStore.builder()
            .serviceName("es") // for managed AWS OS
            .serverUrl(amazonManagedOpenSearch.serverUrl())
            .region(amazonManagedOpenSearch.region())
            .options(
                AwsSdk2TransportOptions.builder()
                    .setCredentials(
                        StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                amazonManagedOpenSearch.accessKey(),
                                amazonManagedOpenSearch.secretKey())))
                    .build())
            .indexName(amazonManagedOpenSearch.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(openSearchEmbeddingStore);
  }
}
