/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.camunda.connector.http.client.client.apache.proxy.ProxyRoutePlanner;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import io.camunda.connector.util.ProxyUtil;
import java.net.URISyntaxException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;

public class OpenSearchVectorStoreFactory {

  public static final String AMAZON_SIGNING_SERVICE_NAME = "es";
  private final ProxyConfiguration proxyConfig;

  public OpenSearchVectorStoreFactory(ProxyConfiguration proxyConfig) {
    this.proxyConfig = proxyConfig;
  }

  public ClosableEmbeddingStore<TextSegment> createOpenSearchVectorStore(
      OpenSearchVectorStore openSearchVectorStore) {
    final var openSearch = openSearchVectorStore.openSearch();
    OpenSearchEmbeddingStore.Builder builder =
        OpenSearchEmbeddingStore.builder().indexName(openSearch.indexName());

    configureOpenSearchProxy(builder, openSearch);

    OpenSearchEmbeddingStore openSearchEmbeddingStore = builder.build();
    return ClosableEmbeddingStore.wrap(openSearchEmbeddingStore);
  }

  private void configureOpenSearchProxy(
      OpenSearchEmbeddingStore.Builder builder, OpenSearchVectorStore.Configuration openSearch) {
    HttpHost openSearchHost;
    try {
      openSearchHost = HttpHost.create(openSearch.baseUrl());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid base URL", e);
    }
    OpenSearchTransport transport =
        ApacheHttpClient5TransportBuilder.builder(openSearchHost)
            .setMapper(new JacksonJsonpMapper())
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    configureHttpAsyncClientBuilder(httpClientBuilder, openSearchHost, openSearch))
            .build();

    builder.openSearchClient(new OpenSearchClient(transport));
  }

  private HttpAsyncClientBuilder configureHttpAsyncClientBuilder(
      HttpAsyncClientBuilder httpClientBuilder,
      HttpHost openSearchHost,
      OpenSearchVectorStore.Configuration openSearch) {
    httpClientBuilder.setConnectionManager(
        PoolingAsyncClientConnectionManagerBuilder.create().build());
    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    // Proxy config from system properties is used when connector proxy env vars are
    // not present.
    // Note that some other system properties, such as http.keepAlive, are used too.
    httpClientBuilder.useSystemProperties();

    // We need to explicitly disable content compression for opensearch-client 2.x and httpclient5
    // >= 5.6
    httpClientBuilder.disableContentCompression();

    if (!isNullOrBlank(openSearch.userName()) && !isNullOrBlank(openSearch.password())) {
      credentialsProvider.setCredentials(
          new AuthScope(openSearchHost),
          new UsernamePasswordCredentials(
              openSearch.userName(), openSearch.password().toCharArray()));
    }

    proxyConfig
        .getProxyDetails(openSearchHost.getSchemeName())
        .ifPresent(
            proxyDetails -> {
              HttpHost proxyHost =
                  new HttpHost(proxyDetails.scheme(), proxyDetails.host(), proxyDetails.port());

              if (proxyDetails.hasCredentials()) {
                credentialsProvider.setCredentials(
                    new AuthScope(proxyHost),
                    new UsernamePasswordCredentials(
                        proxyDetails.user(), proxyDetails.password().toCharArray()));
              }

              httpClientBuilder.setRoutePlanner(new ProxyRoutePlanner(proxyHost));
            });

    return httpClientBuilder;
  }

  public ClosableEmbeddingStore<TextSegment> createAmazonManagedOpenSearchVectorStore(
      AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore) {
    final var amazonManagedOpenSearch =
        amazonManagedOpenSearchVectorStore.amazonManagedOpensearch();

    HttpHost openSearchHost;
    try {
      openSearchHost = HttpHost.create(amazonManagedOpenSearch.serverUrl());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid server URL", e);
    }

    SdkHttpClient httpClient =
        ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, openSearchHost.getSchemeName());

    Region selectedRegion = Region.of(amazonManagedOpenSearch.region());
    OpenSearchTransport transport =
        new AwsSdk2Transport(
            httpClient,
            amazonManagedOpenSearch.serverUrl(),
            AMAZON_SIGNING_SERVICE_NAME,
            selectedRegion,
            AwsSdk2TransportOptions.builder()
                .setCredentials(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            amazonManagedOpenSearch.accessKey(),
                            amazonManagedOpenSearch.secretKey())))
                .build());

    OpenSearchEmbeddingStore openSearchEmbeddingStore =
        OpenSearchEmbeddingStore.builder()
            .openSearchClient(new OpenSearchClient(transport))
            .indexName(amazonManagedOpenSearch.indexName())
            .build();
    return ClosableEmbeddingStore.wrap(openSearchEmbeddingStore);
  }
}
