/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.azure.core.http.ProxyOptions;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

public class ChatModelHttpProxySupport {
  private static final Logger LOG = LoggerFactory.getLogger(ChatModelHttpProxySupport.class);

  private final ProxyConfiguration proxyConfiguration;
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public ChatModelHttpProxySupport(
      ProxyConfiguration proxyConfiguration,
      JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator) {
    this.proxyConfiguration = proxyConfiguration;
    this.jdkHttpClientProxyConfigurator = jdkHttpClientProxyConfigurator;
  }

  /**
   * Builds a JDK {@link HttpClient} and returns it together with a {@link JdkHttpClientBuilder}
   * configured to reuse that exact instance. Callers can close the returned {@code httpClient}
   * after the request to release the connection pool and selector thread.
   *
   * <p>The supplied {@code connectTimeout} is applied to the real client before construction so
   * that the inner {@link JdkHttpClientBuilder} does not build a second client when {@code
   * JdkHttpClient} is constructed from the builder.
   */
  public JdkHttpClientHandle createJdkHttpClient(Duration connectTimeout) {
    final var httpClientBuilder = HttpClient.newBuilder().connectTimeout(connectTimeout);
    jdkHttpClientProxyConfigurator.configure(httpClientBuilder);
    final var httpClient = httpClientBuilder.build();
    final var jdkBuilder =
        new JdkHttpClientBuilder().httpClientBuilder(new PrebuiltHttpClientBuilder(httpClient));
    return new JdkHttpClientHandle(httpClient, jdkBuilder);
  }

  /** Pairs the pre-built {@link HttpClient} with a {@link JdkHttpClientBuilder} wrapping it. */
  public record JdkHttpClientHandle(HttpClient httpClient, JdkHttpClientBuilder builder) {}

  /**
   * Wraps a pre-built {@link HttpClient} behind the {@link HttpClient.Builder} interface. All
   * configuration calls are no-ops — settings were already applied before the client was built.
   * {@link #build()} returns the pre-built instance so {@code JdkHttpClient} reuses it rather than
   * creating a second one.
   */
  private static final class PrebuiltHttpClientBuilder implements HttpClient.Builder {
    private final HttpClient httpClient;

    PrebuiltHttpClientBuilder(HttpClient httpClient) {
      this.httpClient = httpClient;
    }

    @Override
    public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
      return this;
    }

    @Override
    public HttpClient.Builder connectTimeout(Duration duration) {
      return this;
    }

    @Override
    public HttpClient.Builder sslContext(SSLContext sslContext) {
      return this;
    }

    @Override
    public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
      return this;
    }

    @Override
    public HttpClient.Builder executor(Executor executor) {
      return this;
    }

    @Override
    public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
      return this;
    }

    @Override
    public HttpClient.Builder version(HttpClient.Version version) {
      return this;
    }

    @Override
    public HttpClient.Builder priority(int priority) {
      return this;
    }

    @Override
    public HttpClient.Builder proxy(ProxySelector proxySelector) {
      return this;
    }

    @Override
    public HttpClient.Builder authenticator(Authenticator authenticator) {
      return this;
    }

    @Override
    public HttpClient build() {
      return httpClient;
    }
  }

  public ApacheHttpClient.Builder createAwsHttpClientBuilder(URI endpointOverride) {
    String schemeName =
        endpointOverride != null ? endpointOverride.getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    return ApacheHttpClient.builder().proxyConfiguration(createAwsProxyConfiguration(schemeName));
  }

  software.amazon.awssdk.http.apache.ProxyConfiguration createAwsProxyConfiguration(
      String schemeName) {
    software.amazon.awssdk.http.apache.ProxyConfiguration.Builder awsProxyConfigBuilder =
        software.amazon.awssdk.http.apache.ProxyConfiguration.builder()
            .useSystemPropertyValues(true);

    proxyConfiguration
        .getProxyDetails(schemeName)
        .ifPresent(
            proxyDetails -> {
              LOG.debug(
                  "Using proxy for target scheme [{}] => [{}://{}:{}]",
                  schemeName,
                  proxyDetails.scheme(),
                  proxyDetails.host(),
                  proxyDetails.port());
              awsProxyConfigBuilder
                  .scheme(proxyDetails.scheme())
                  .endpoint(toUri(proxyDetails))
                  .nonProxyHosts(
                      NonProxyHosts.getNonProxyHostRegexPatterns().collect(Collectors.toSet()));

              if (proxyDetails.hasCredentials()) {
                awsProxyConfigBuilder.username(proxyDetails.user());
                awsProxyConfigBuilder.password(proxyDetails.password());
              }
            });

    return awsProxyConfigBuilder.build();
  }

  public Optional<ProxyOptions> createAzureProxyOptions(String endpoint) {
    final var uri = URI.create(endpoint);
    if (uri.getScheme() == null) {
      throw new IllegalArgumentException("Invalid endpoint URI: " + endpoint);
    }

    // If connector proxy env vars are not present, the Azure OpenAI client will use the system
    // properties for proxy configuration.
    return proxyConfiguration
        .getProxyDetails(uri.getScheme())
        .map(
            proxyDetails -> {
              LOG.debug(
                  "Using proxy for target scheme [{}] and host [{}] => [{}://{}:{}]",
                  uri.getScheme(),
                  uri.getHost(),
                  proxyDetails.scheme(),
                  proxyDetails.host(),
                  proxyDetails.port());
              ProxyOptions proxyOptions =
                  new ProxyOptions(
                      ProxyOptions.Type.HTTP,
                      new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
              proxyOptions.setNonProxyHosts(
                  NonProxyHosts.getNonProxyHostsPatterns()
                      .distinct()
                      .collect(Collectors.joining("|")));
              if (proxyDetails.hasCredentials()) {
                proxyOptions.setCredentials(proxyDetails.user(), proxyDetails.password());
              }
              return proxyOptions;
            });
  }

  private static URI toUri(ProxyConfiguration.ProxyDetails proxyDetails) {
    return URI.create(
        proxyDetails.scheme() + "://" + proxyDetails.host() + ":" + proxyDetails.port());
  }
}
