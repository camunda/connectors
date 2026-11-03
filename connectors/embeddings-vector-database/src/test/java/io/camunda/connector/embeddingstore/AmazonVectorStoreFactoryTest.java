/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.community.store.embedding.dynamodb.DynamoDbEmbeddingStore;
import dev.langchain4j.community.store.embedding.s3.S3VectorsEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.util.ProxyUtil;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

class AmazonVectorStoreFactoryTest {

  enum Store {
    S3_VECTORS(
        factory ->
            factory.createS3VectorsVectorStore(
                EmbeddingsVectorStoreFixture.createAmazonS3VectorsVectorStore()),
        S3VectorsEmbeddingStore.class),
    DYNAMODB(
        factory ->
            factory.createDynamoDbVectorStore(
                EmbeddingsVectorStoreFixture.createAmazonDynamoDbVectorStore()),
        DynamoDbEmbeddingStore.class);

    final Function<AmazonVectorStoreFactory, ClosableEmbeddingStore<TextSegment>> create;
    final Class<?> expectedType;

    Store(
        Function<AmazonVectorStoreFactory, ClosableEmbeddingStore<TextSegment>> create,
        Class<?> expectedType) {
      this.create = create;
      this.expectedType = expectedType;
    }
  }

  @ParameterizedTest
  @EnumSource(Store.class)
  void createsStoreWithProxyAwareHttpClientAndClosesIt(Store store) {
    var proxyConfig = ProxyConfiguration.NONE;
    SdkHttpClient httpClient = spy(ApacheHttpClient.create());

    try (var proxyUtil = mockStatic(ProxyUtil.class)) {
      proxyUtil
          .when(() ->
              ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(httpClient);

      var embeddingStore = store.create.apply(new AmazonVectorStoreFactory(proxyConfig));

      assertThat(embeddingStore.getEmbeddingStore()).isInstanceOf(store.expectedType);
      proxyUtil.verify(() ->
          ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, ProxyConfiguration.SCHEME_HTTPS));

      embeddingStore.close();
      verify(httpClient).close();
    }
  }
}
