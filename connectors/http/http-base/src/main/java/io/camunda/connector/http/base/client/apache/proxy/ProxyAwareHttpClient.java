package io.camunda.connector.http.base.client.apache.proxy;

import java.io.Closeable;
import java.io.IOException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for creating a {@link CloseableHttpClient} with proxy support. This class is thread-safe.
 */
public class ProxyAwareHttpClient implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyAwareHttpClient.class);

  private final ProxyHandler proxyHandler = new ProxyHandler();
  private final TimeoutConfiguration timeoutConfiguration;
  private final ProxyContext proxyContext;
  private final CloseableHttpClient client;

  public record TimeoutConfiguration(int connectionTimeoutInSeconds, int readTimeoutInSeconds) {}

  public record ProxyContext(String scheme, String host) {}

  public ProxyAwareHttpClient(
      TimeoutConfiguration timeoutConfiguration, ProxyContext proxyContext) {
    this.timeoutConfiguration = timeoutConfiguration;
    this.proxyContext = proxyContext;
    this.client = createClient();
  }

  @Override
  public void close() throws IOException {
    if (client != null) {
      client.close();
    }
  }

  public <T> T execute(ClassicHttpRequest request, HttpClientResponseHandler<T> responseHandler)
      throws IOException {
    return client.execute(request, responseHandler);
  }

  private CloseableHttpClient createClient() {
    var builder = createHttpClientBuilder();

    setProxyIfConfigured(proxyContext, builder);

    builder
        .setDefaultRequestConfig(getRequestTimeoutConfig(timeoutConfiguration))
        .useSystemProperties();

    return builder.build();
  }

  private void setProxyIfConfigured(ProxyContext proxyContext, HttpClientBuilder builder) {
    proxyHandler
        .getProxyDetails(proxyContext.scheme())
        .ifPresent(
            proxyDetails -> {
              HttpHost proxyHttpHost =
                  new HttpHost(proxyDetails.scheme(), proxyDetails.host(), proxyDetails.port());
              LOG.debug(
                  "Using proxy for target scheme [{}] and host [{}] => [{}]",
                  proxyContext.scheme(),
                  proxyContext.host(),
                  proxyHttpHost);
              builder.setDefaultCredentialsProvider(
                  proxyHandler.getCredentialsProvider(proxyContext.scheme()));
              builder.setRoutePlanner(new ProxyRoutePlanner(proxyHttpHost));
            });
  }

  private HttpClientBuilder createHttpClientBuilder() {
    return HttpClients.custom()
        .setConnectionManager(createConnectionManager())
        .disableRedirectHandling();
  }

  private PoolingHttpClientConnectionManager createConnectionManager() {
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(Integer.MAX_VALUE);
    connectionManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);

    // Socket config
    connectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build());

    return connectionManager;
  }

  private RequestConfig getRequestTimeoutConfig(TimeoutConfiguration timeoutConfiguration) {
    return RequestConfig.custom()
        .setConnectionRequestTimeout(
            Timeout.ofSeconds(timeoutConfiguration.connectionTimeoutInSeconds()))
        .setResponseTimeout(Timeout.ofSeconds(timeoutConfiguration.readTimeoutInSeconds()))
        .build();
  }
}
