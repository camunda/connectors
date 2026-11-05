/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AmazonDynamoDbVectorStore;
import io.camunda.connector.model.embedding.vector.store.AmazonS3VectorsVectorStore;
import io.camunda.connector.util.ProxyUtil;
import java.time.Duration;
import java.util.function.Function;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

public class AmazonVectorStoreFactory {

  private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);

  private final ProxyConfiguration proxyConfig;

  public AmazonVectorStoreFactory(ProxyConfiguration proxyConfig) {
    this.proxyConfig = proxyConfig;
  }

  public ClosableEmbeddingStore<TextSegment> createS3VectorsVectorStore(
      AmazonS3VectorsVectorStore amazonS3VectorsVectorStore) {
    final var config = amazonS3VectorsVectorStore.amazonS3Vectors();
    return createStore(
        S3VectorsClient.builder(),
        config.region(),
        config.accessKey(),
        config.secretKey(),
        client ->
            new AmazonS3VectorsEmbeddingStore(
                client, config.region(), config.vectorBucketName(), config.indexName()));
  }

  public ClosableEmbeddingStore<TextSegment> createDynamoDbVectorStore(
      AmazonDynamoDbVectorStore amazonDynamoDbVectorStore) {
    final var config = amazonDynamoDbVectorStore.amazonDynamoDb();
    return createStore(
        DynamoDbClient.builder(),
        config.region(),
        config.accessKey(),
        config.secretKey(),
        client ->
            new AmazonDynamoDbEmbeddingStore(
                client, config.region(), config.tableName(), config.indexName()));
  }

  private <C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsSyncClientBuilder<B, C>>
      ClosableEmbeddingStore<TextSegment> createStore(
          B builder,
          String region,
          String accessKey,
          String secretKey,
          Function<C, AwsVectorEmbeddingStore> storeFactory) {
    final var httpClient =
        ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, ProxyConfiguration.SCHEME_HTTPS);
    try {
      final C client =
          builder
              .httpClient(httpClient)
              .region(Region.of(region))
              .credentialsProvider(
                  StaticCredentialsProvider.create(
                      AwsBasicCredentials.create(accessKey, secretKey)))
              .overrideConfiguration(config -> config.apiCallTimeout(API_CALL_TIMEOUT))
              .build();
      return ClosableEmbeddingStore.wrap(
          storeFactory.apply(client), () -> close(client, httpClient));
    } catch (RuntimeException e) {
      httpClient.close();
      throw e;
    }
  }

  // The SDK does not close an HTTP client that was passed to its builder.
  private static void close(SdkClient client, SdkHttpClient httpClient) {
    try (httpClient) {
      client.close();
    }
  }
}
