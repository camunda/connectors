/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.assertj.core.api.ThrowingConsumer;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

class ElasticsearchVectorStoreFactoryTest {

  private static final String ELASTICSEARCH_HOST = "elastic.local";
  private static final int ELASTICSEARCH_PORT = 9200;
  private static final String ELASTICSEARCH_USERNAME = "elastic";
  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USERNAME = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";

  @Test
  void createElasticsearchVectorStoreWithoutProxy() {
    var proxyConfig = mock(ProxyConfiguration.class);
    when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS)).thenReturn(Optional.empty());

    testElasticsearchConfiguration(
        proxyConfig,
        EmbeddingsVectorStoreFixture.createElasticsearchVectorStore(),
        credentialsProvider -> {
          // Verify Elasticsearch credentials are set
          var esCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost("elastic.local", 9200, ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(esCredentials).isNotNull();
          assertThat(esCredentials).isInstanceOf(UsernamePasswordCredentials.class);
          assertThat(((UsernamePasswordCredentials) esCredentials).getUserName())
              .isEqualTo("elastic");
        });
  }

  @Test
  void configureElasticsearchProxyWithoutCredentials() {
    var proxyDetails =
        new ProxyConfiguration.ProxyDetails(
            ProxyConfiguration.SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);

    var proxyConfig = mock(ProxyConfiguration.class);
    when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
        .thenReturn(Optional.of(proxyDetails));

    testElasticsearchConfiguration(
        proxyConfig,
        EmbeddingsVectorStoreFixture.createElasticsearchVectorStore(),
        credentialsProvider -> {
          // Verify Elasticsearch credentials are set
          var esCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost(
                          ELASTICSEARCH_HOST,
                          ELASTICSEARCH_PORT,
                          ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(esCredentials).isNotNull();
          assertThat(esCredentials).isInstanceOf(UsernamePasswordCredentials.class);
          assertThat(((UsernamePasswordCredentials) esCredentials).getUserName())
              .isEqualTo(ELASTICSEARCH_USERNAME);

          // Verify NO proxy credentials are set (proxy has no credentials)
          var proxyCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost(PROXY_HOST, PROXY_PORT, ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(proxyCredentials).isNull();
        },
        httpClientBuilder -> {
          // Verify proxy route planner was set (proxy host configured)
          verify(httpClientBuilder)
              .setRoutePlanner(
                  argThat(
                      routePlanner ->
                          routePlanner
                              instanceof ElasticsearchVectorStoreFactory.ProxyRoutePlanner));
        });
  }

  @Test
  void configureElasticsearchProxyWithCredentials() {
    var proxyDetails =
        new ProxyConfiguration.ProxyDetails(
            ProxyConfiguration.SCHEME_HTTPS,
            PROXY_HOST,
            PROXY_PORT,
            PROXY_USERNAME,
            PROXY_PASSWORD);

    var proxyConfig = mock(ProxyConfiguration.class);
    when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
        .thenReturn(Optional.of(proxyDetails));

    testElasticsearchConfiguration(
        proxyConfig,
        EmbeddingsVectorStoreFixture.createElasticsearchVectorStore(),
        credentialsProvider -> {
          // Verify Elasticsearch credentials are set
          var esCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost("elastic.local", 9200, ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(esCredentials).isNotNull();
          assertThat(esCredentials).isInstanceOf(UsernamePasswordCredentials.class);
          assertThat(((UsernamePasswordCredentials) esCredentials).getUserName())
              .isEqualTo("elastic");

          // Verify proxy credentials ARE set (proxy has credentials)
          var proxyCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost(PROXY_HOST, PROXY_PORT, ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(proxyCredentials).isNotNull();
          assertThat(proxyCredentials).isInstanceOf(UsernamePasswordCredentials.class);
          assertThat(((UsernamePasswordCredentials) proxyCredentials).getUserName())
              .isEqualTo(PROXY_USERNAME);
        },
        httpClientBuilder -> {
          // Verify proxy route planner was set
          verify(httpClientBuilder)
              .setRoutePlanner(
                  argThat(
                      routePlanner ->
                          routePlanner
                              instanceof ElasticsearchVectorStoreFactory.ProxyRoutePlanner));
        });
  }

  @Test
  void createElasticsearchVectorStoreWithoutAuthentication() {
    var proxyConfig = mock(ProxyConfiguration.class);
    when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS)).thenReturn(Optional.empty());

    testElasticsearchConfiguration(
        proxyConfig,
        new ElasticsearchVectorStore(
            new ElasticsearchVectorStore.Configuration(
                "https://" + ELASTICSEARCH_HOST + ":" + ELASTICSEARCH_PORT,
                null,
                null,
                "embeddings_idx")),
        credentialsProvider -> {
          // Verify NO Elasticsearch credentials are set (username was null)
          var esCredentials =
              credentialsProvider.getCredentials(
                  new AuthScope(
                      new HttpHost(
                          ELASTICSEARCH_HOST,
                          ELASTICSEARCH_PORT,
                          ProxyConfiguration.SCHEME_HTTPS)));
          assertThat(esCredentials).isNull();
        });
  }

  private void testElasticsearchConfiguration(
      ProxyConfiguration proxyConfig,
      ElasticsearchVectorStore elasticsearchVectorStore,
      ThrowingConsumer<CredentialsProvider> credentialsVerifications) {
    testElasticsearchConfiguration(
        proxyConfig, elasticsearchVectorStore, credentialsVerifications, httpClientBuilder -> {});
  }

  private void testElasticsearchConfiguration(
      ProxyConfiguration proxyConfig,
      ElasticsearchVectorStore elasticsearchVectorStore,
      ThrowingConsumer<CredentialsProvider> credentialsVerifications,
      ThrowingConsumer<HttpAsyncClientBuilder> httpClientBuilderVerifications) {

    var factory = new ElasticsearchVectorStoreFactory(proxyConfig);

    var mockRestClient = mock(RestClient.class);
    var restClientBuilder = mock(RestClientBuilder.class);
    when(restClientBuilder.build()).thenReturn(mockRestClient);

    var httpClientBuilder = spy(HttpAsyncClientBuilder.create());

    try (var restClientMock = mockStatic(RestClient.class);
        var embeddingStoreMock =
            mockStatic(ElasticsearchEmbeddingStore.class, Answers.CALLS_REAL_METHODS);
        var httpAsyncClientBuilderMock = mockStatic(HttpAsyncClientBuilder.class)) {

      restClientMock
          .when(() -> RestClient.builder(any(HttpHost.class)))
          .thenReturn(restClientBuilder);
      httpAsyncClientBuilderMock.when(HttpAsyncClientBuilder::create).thenReturn(httpClientBuilder);

      var mockElasticsearchBuilder = spy(ElasticsearchEmbeddingStore.builder());
      embeddingStoreMock
          .when(ElasticsearchEmbeddingStore::builder)
          .thenReturn(mockElasticsearchBuilder);

      ArgumentCaptor<RestClientBuilder.HttpClientConfigCallback> callbackCaptor =
          ArgumentCaptor.forClass(RestClientBuilder.HttpClientConfigCallback.class);

      when(restClientBuilder.setHttpClientConfigCallback(callbackCaptor.capture()))
          .thenReturn(restClientBuilder);

      var store = factory.createElasticsearchVectorStore(elasticsearchVectorStore);

      assertThat(store).isNotNull();
      verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
      verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);

      // Invoke the callback to verify configuration
      var callback = callbackCaptor.getValue();
      callback.customizeHttpClient(httpClientBuilder);

      verify(httpClientBuilder).useSystemProperties();
      verify(httpClientBuilder).setDefaultCredentialsProvider(any());

      ArgumentCaptor<CredentialsProvider> credentialsProviderCaptor =
          ArgumentCaptor.forClass(CredentialsProvider.class);
      verify(httpClientBuilder).setDefaultCredentialsProvider(credentialsProviderCaptor.capture());

      var credentialsProvider = credentialsProviderCaptor.getValue();

      credentialsVerifications.accept(credentialsProvider);
      httpClientBuilderVerifications.accept(httpClientBuilder);
    }
  }
}
