/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.community.store.embedding.dynamodb.DynamoDbEmbeddingStore;
import dev.langchain4j.community.store.embedding.s3.S3VectorsEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AmazonDynamoDbVectorStore;
import io.camunda.connector.model.embedding.vector.store.AmazonS3VectorsVectorStore;
import io.camunda.connector.util.ProxyUtil;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

public class AmazonVectorStoreFactory {

  private final ProxyConfiguration proxyConfig;

  public AmazonVectorStoreFactory(ProxyConfiguration proxyConfig) {
    this.proxyConfig = proxyConfig;
  }

  public ClosableEmbeddingStore<TextSegment> createS3VectorsVectorStore(
      AmazonS3VectorsVectorStore amazonS3VectorsVectorStore) {
    final var config = amazonS3VectorsVectorStore.amazonS3Vectors();
    final var httpClient = createHttpClient();
    final var client =
        S3VectorsClient.builder()
            .httpClient(httpClient)
            .region(Region.of(config.region()))
            .credentialsProvider(credentialsProvider(config.accessKey(), config.secretKey()))
            .build();

    final var embeddingStore =
        S3VectorsEmbeddingStore.builder()
            .s3VectorsClient(client)
            .vectorBucketName(config.vectorBucketName())
            .indexName(config.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(embeddingStore, () -> close(client, httpClient));
  }

  public ClosableEmbeddingStore<TextSegment> createDynamoDbVectorStore(
      AmazonDynamoDbVectorStore amazonDynamoDbVectorStore) {
    final var config = amazonDynamoDbVectorStore.amazonDynamoDb();
    final var httpClient = createHttpClient();
    final var client =
        DynamoDbClient.builder()
            .httpClient(httpClient)
            .region(Region.of(config.region()))
            .credentialsProvider(credentialsProvider(config.accessKey(), config.secretKey()))
            .build();

    final var embeddingStore =
        DynamoDbEmbeddingStore.builder()
            .dynamoDbClient(client)
            .tableName(config.tableName())
            .indexName(config.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(embeddingStore, () -> close(client, httpClient));
  }

  private SdkHttpClient createHttpClient() {
    return ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, ProxyConfiguration.SCHEME_HTTPS);
  }

  private static StaticCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  // The SDK does not close an HTTP client that was passed to its builder.
  private static void close(SdkClient client, SdkHttpClient httpClient) {
    try (httpClient) {
      client.close();
    }
  }
}
