/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchVectorStoreFactory {

  private final ProxyConfiguration proxyConfig;

  public ElasticsearchVectorStoreFactory(ProxyConfiguration proxyConfig) {
    this.proxyConfig = proxyConfig;
  }

  public ClosableEmbeddingStore<TextSegment> createElasticsearchVectorStore(
      ElasticsearchVectorStore elasticsearchVectorStore) {
    final var elasticsearch = elasticsearchVectorStore.elasticsearch();
    HttpHost httpHost = HttpHost.create(elasticsearch.baseUrl());
    RestClientBuilder restClientBuilder = RestClient.builder(httpHost);

    ElasticsearchHttpClientConfigCallback httpClientConfigCallback =
        new ElasticsearchHttpClientConfigCallback();
    restClientBuilder.setHttpClientConfigCallback(httpClientConfigCallback);

    if (!isNullOrBlank(elasticsearch.userName())) {
      httpClientConfigCallback.setCredentials(
          new AuthScope(httpHost),
          new UsernamePasswordCredentials(elasticsearch.userName(), elasticsearch.password()));
    }

    proxyConfig
        .getProxyDetails(httpHost.getSchemeName())
        .ifPresent(
            proxyDetails -> {
              HttpHost proxyHost =
                  new HttpHost(proxyDetails.host(), proxyDetails.port(), proxyDetails.scheme());

              httpClientConfigCallback.setProxyHost(proxyHost);
              if (proxyDetails.hasCredentials()) {
                httpClientConfigCallback.setCredentials(
                    new AuthScope(proxyHost),
                    new UsernamePasswordCredentials(proxyDetails.user(), proxyDetails.password()));
              }
            });

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

  static class ElasticsearchHttpClientConfigCallback
      implements RestClientBuilder.HttpClientConfigCallback {

    private final CredentialsProvider provider = new BasicCredentialsProvider();
    private HttpHost proxyHost;

    @Override
    public HttpAsyncClientBuilder customizeHttpClient(
        HttpAsyncClientBuilder httpAsyncClientBuilder) {
      // Proxy config from system properties is used when connector proxy env vars are not present.
      // Note that some other system properties, such as http.keepAlive, are used too.
      httpAsyncClientBuilder.useSystemProperties();

      httpAsyncClientBuilder.setDefaultCredentialsProvider(provider);
      if (proxyHost != null) {
        httpAsyncClientBuilder.setRoutePlanner(new ProxyRoutePlanner(proxyHost));
      }
      return httpAsyncClientBuilder;
    }

    public void setCredentials(AuthScope authScope, Credentials credentials) {
      provider.setCredentials(authScope, credentials);
    }

    public void setProxyHost(HttpHost proxyHost) {
      this.proxyHost = proxyHost;
    }
  }

  static class ProxyRoutePlanner extends DefaultProxyRoutePlanner {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyRoutePlanner.class.getName());

    public ProxyRoutePlanner(HttpHost proxy) {
      super(proxy);
    }

    @Override
    protected HttpHost determineProxy(HttpHost target, HttpRequest request, HttpContext context)
        throws HttpException {
      if (NonProxyHosts.isNonProxyHost(target.getHostName())) {
        LOG.debug(
            "Not using proxy for target host [{}] as it matched either system properties (http.nonProxyHosts) or environment variables ({})",
            target.getHostName(),
            NonProxyHosts.CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR);
        return null;
      }
      var proxy = super.determineProxy(target, request, context);
      LOG.debug("Using proxy for target host [{}] => [{}]", target.getHostName(), proxy);
      return proxy;
    }
  }
}
