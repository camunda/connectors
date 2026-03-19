/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.fixture.EmbeddingsVectorStoreFixture;
import io.camunda.connector.http.client.client.apache.proxy.ProxyRoutePlanner;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import java.util.Optional;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

class OpenSearchVectorStoreFactoryTest {

  private static final String OPENSEARCH_HOST = "opensearch.local";
  private static final int OPENSEARCH_PORT = 9200;
  private static final String OPENSEARCH_USERNAME = "opensearch";
  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USERNAME = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";

  @Nested
  class OpenSearchVectorStoreTests {

    @Test
    void createOpenSearchVectorStoreWithoutProxy() {
      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.empty());

      testOpenSearchConfiguration(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(),
          credentialsProvider -> {
            // Verify OpenSearch credentials are set
            var osCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(
                            ProxyConfiguration.SCHEME_HTTPS, OPENSEARCH_HOST, OPENSEARCH_PORT)),
                    null);
            assertThat(osCredentials).isNotNull();
            assertThat(osCredentials.getUserPrincipal().getName()).isEqualTo(OPENSEARCH_USERNAME);
          });
    }

    @Test
    void configureOpenSearchProxyWithoutCredentials() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails(
              ProxyConfiguration.SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);

      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.of(proxyDetails));

      testOpenSearchConfiguration(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(),
          credentialsProvider -> {
            // Verify OpenSearch credentials are set
            var osCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(
                            ProxyConfiguration.SCHEME_HTTPS, OPENSEARCH_HOST, OPENSEARCH_PORT)),
                    null);
            assertThat(osCredentials).isNotNull();
            assertThat(osCredentials.getUserPrincipal().getName()).isEqualTo(OPENSEARCH_USERNAME);

            // Verify NO proxy credentials are set (proxy has no credentials)
            var proxyCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(ProxyConfiguration.SCHEME_HTTPS, PROXY_HOST, PROXY_PORT)),
                    null);
            assertThat(proxyCredentials).isNull();
          },
          httpClientBuilder -> {
            // Verify proxy route planner was set (proxy host configured)
            verify(httpClientBuilder)
                .setRoutePlanner(
                    argThat(routePlanner -> routePlanner instanceof ProxyRoutePlanner));
          });
    }

    @Test
    void configureOpenSearchProxyWithCredentials() {
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

      testOpenSearchConfiguration(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createOpenSearchVectorStore(),
          credentialsProvider -> {
            // Verify OpenSearch credentials are set
            var osCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(ProxyConfiguration.SCHEME_HTTPS, "opensearch.local", 9200)),
                    null);
            assertThat(osCredentials).isNotNull();
            assertThat(osCredentials.getUserPrincipal().getName()).isEqualTo("opensearch");

            // Verify proxy credentials ARE set (proxy has credentials)
            var proxyCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(ProxyConfiguration.SCHEME_HTTPS, "proxy.example.com", 8080)),
                    null);
            assertThat(proxyCredentials).isNotNull();
            assertThat(proxyCredentials.getUserPrincipal().getName()).isEqualTo("proxyuser");
          },
          httpClientBuilder -> {
            // Verify proxy route planner was set
            verify(httpClientBuilder)
                .setRoutePlanner(
                    argThat(routePlanner -> routePlanner instanceof ProxyRoutePlanner));
          });
    }

    @Test
    void createOpenSearchVectorStoreWithoutAuthentication() {
      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.empty());

      testOpenSearchConfiguration(
          proxyConfig,
          new OpenSearchVectorStore(
              new OpenSearchVectorStore.Configuration(
                  "https://" + OPENSEARCH_HOST + ":" + OPENSEARCH_PORT,
                  null,
                  null,
                  "embeddings_idx")),
          credentialsProvider -> {
            // Verify NO OpenSearch credentials are set (username was null)
            var osCredentials =
                credentialsProvider.getCredentials(
                    new AuthScope(
                        new HttpHost(
                            ProxyConfiguration.SCHEME_HTTPS, OPENSEARCH_HOST, OPENSEARCH_PORT)),
                    null);
            assertThat(osCredentials).isNull();
          });
    }

    private void testOpenSearchConfiguration(
        ProxyConfiguration proxyConfig,
        OpenSearchVectorStore openSearchVectorStore,
        ThrowingConsumer<BasicCredentialsProvider> credentialsVerifications) {
      testOpenSearchConfiguration(
          proxyConfig, openSearchVectorStore, credentialsVerifications, httpClientBuilder -> {});
    }

    private void testOpenSearchConfiguration(
        ProxyConfiguration proxyConfig,
        OpenSearchVectorStore openSearchVectorStore,
        ThrowingConsumer<BasicCredentialsProvider> credentialsVerifications,
        ThrowingConsumer<HttpAsyncClientBuilder> httpClientBuilderVerifications) {

      var factory = new OpenSearchVectorStoreFactory(proxyConfig);

      var httpClientBuilder = spy(HttpAsyncClientBuilder.create());

      try (var httpAsyncClientBuilderMock = mockStatic(HttpAsyncClientBuilder.class)) {

        httpAsyncClientBuilderMock
            .when(HttpAsyncClientBuilder::create)
            .thenReturn(httpClientBuilder);

        // Allow factory to execute normally - it will build the transport which will call our spy
        var store = factory.createOpenSearchVectorStore(openSearchVectorStore);

        assertThat(store).isNotNull();
        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);

        verify(httpClientBuilder).useSystemProperties();
        verify(httpClientBuilder).setDefaultCredentialsProvider(any());

        ArgumentCaptor<BasicCredentialsProvider> credentialsProviderCaptor =
            ArgumentCaptor.forClass(BasicCredentialsProvider.class);
        verify(httpClientBuilder)
            .setDefaultCredentialsProvider(credentialsProviderCaptor.capture());

        var credentialsProvider = credentialsProviderCaptor.getValue();

        credentialsVerifications.accept(credentialsProvider);
        httpClientBuilderVerifications.accept(httpClientBuilder);
      }
    }
  }

  @Nested
  class AmazonManagedOpenSearchVectorStoreTests {

    @Test
    void createAmazonManagedOpenSearchVectorStoreWithoutProxy() {
      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.empty());

      testAmazonManagedOpenSearchConfiguration(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(),
          awsProxyConfig -> {
            assertThat(awsProxyConfig.host()).isNull();
            assertThat(awsProxyConfig.username()).isNull();
            assertThat(awsProxyConfig.password()).isNull();
          });
    }

    @Test
    void configureAmazonManagedOpenSearchProxyWithoutCredentials() {
      var proxyDetails =
          new ProxyConfiguration.ProxyDetails(
              ProxyConfiguration.SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);

      var proxyConfig = mock(ProxyConfiguration.class);
      when(proxyConfig.getProxyDetails(ProxyConfiguration.SCHEME_HTTPS))
          .thenReturn(Optional.of(proxyDetails));

      testAmazonManagedOpenSearchConfigurationWithProxy(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(),
          false); // no credentials
    }

    @Test
    void configureAmazonManagedOpenSearchProxyWithCredentials() {
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

      testAmazonManagedOpenSearchConfigurationWithProxy(
          proxyConfig,
          EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore(),
          true); // with credentials
    }

    private void testAmazonManagedOpenSearchConfiguration(
        ProxyConfiguration proxyConfig,
        AmazonManagedOpenSearchVectorStore vectorStore,
        ThrowingConsumer<software.amazon.awssdk.http.apache.ProxyConfiguration>
            awsProxyConfigAssertion) {
      var factory = new OpenSearchVectorStoreFactory(proxyConfig);

      try (var apacheHttpClientStatic = mockStatic(ApacheHttpClient.class);
          var awsProxyConfigBuilderStatic =
              mockStatic(software.amazon.awssdk.http.apache.ProxyConfiguration.class)) {
        var apacheHttpClientBuilder = mock(ApacheHttpClient.Builder.class, RETURNS_SELF);
        var awsProxyConfigBuilder =
            mock(software.amazon.awssdk.http.apache.ProxyConfiguration.Builder.class, RETURNS_SELF);
        var awsProxyConfig = mock(software.amazon.awssdk.http.apache.ProxyConfiguration.class);

        apacheHttpClientStatic.when(ApacheHttpClient::builder).thenReturn(apacheHttpClientBuilder);
        awsProxyConfigBuilderStatic
            .when(software.amazon.awssdk.http.apache.ProxyConfiguration::builder)
            .thenReturn(awsProxyConfigBuilder);

        when(awsProxyConfigBuilder.build()).thenReturn(awsProxyConfig);
        when(apacheHttpClientBuilder.build())
            .thenReturn(mock(software.amazon.awssdk.http.SdkHttpClient.class));

        var captor =
            ArgumentCaptor.forClass(software.amazon.awssdk.http.apache.ProxyConfiguration.class);
        factory.createAmazonManagedOpenSearchVectorStore(vectorStore);

        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);
        verify(awsProxyConfigBuilder, never()).nonProxyHosts(any());
        verify(apacheHttpClientBuilder).proxyConfiguration(captor.capture());
        awsProxyConfigAssertion.accept(captor.getValue());
      }
    }

    private void testAmazonManagedOpenSearchConfigurationWithProxy(
        ProxyConfiguration proxyConfig,
        AmazonManagedOpenSearchVectorStore vectorStore,
        boolean hasCredentials) {
      var factory = new OpenSearchVectorStoreFactory(proxyConfig);

      try (var apacheHttpClientStatic = mockStatic(ApacheHttpClient.class);
          var awsProxyConfigBuilderStatic =
              mockStatic(software.amazon.awssdk.http.apache.ProxyConfiguration.class)) {
        var apacheHttpClientBuilder = mock(ApacheHttpClient.Builder.class, RETURNS_SELF);
        var awsProxyConfigBuilder =
            mock(software.amazon.awssdk.http.apache.ProxyConfiguration.Builder.class, RETURNS_SELF);
        var awsProxyConfig = mock(software.amazon.awssdk.http.apache.ProxyConfiguration.class);

        apacheHttpClientStatic.when(ApacheHttpClient::builder).thenReturn(apacheHttpClientBuilder);
        awsProxyConfigBuilderStatic
            .when(software.amazon.awssdk.http.apache.ProxyConfiguration::builder)
            .thenReturn(awsProxyConfigBuilder);

        when(awsProxyConfigBuilder.build()).thenReturn(awsProxyConfig);
        when(apacheHttpClientBuilder.build())
            .thenReturn(mock(software.amazon.awssdk.http.SdkHttpClient.class));

        factory.createAmazonManagedOpenSearchVectorStore(vectorStore);

        verify(proxyConfig).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);
        verify(proxyConfig, never()).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);
        verify(awsProxyConfigBuilder).scheme(ProxyConfiguration.SCHEME_HTTPS);
        verify(awsProxyConfigBuilder).endpoint(any());
        verify(awsProxyConfigBuilder).nonProxyHosts(any());

        if (hasCredentials) {
          verify(awsProxyConfigBuilder).username(PROXY_USERNAME);
          verify(awsProxyConfigBuilder).password(PROXY_PASSWORD);
        } else {
          verify(awsProxyConfigBuilder, never()).username(anyString());
          verify(awsProxyConfigBuilder, never()).password(anyString());
        }
        verify(apacheHttpClientBuilder).proxyConfiguration(awsProxyConfig);
      }
    }
  }
}
