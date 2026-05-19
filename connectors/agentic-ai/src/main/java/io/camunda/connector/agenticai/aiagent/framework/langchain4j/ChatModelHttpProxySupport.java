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
   * Configures an {@link HttpClient.Builder} with proxy settings, then wraps it in a {@link
   * CloseableJdkHttpClientBuilder}. Callers set timeouts on the returned builder directly (e.g.
   * {@code .connectTimeout(...).readTimeout(...)}), pass it to a langchain4j model builder as the
   * {@code httpClientBuilder}, then pass it to {@link CloseableChatModelDelegate} as the resource
   * to close.
   */
  public CloseableJdkHttpClientBuilder createJdkHttpClientBuilder() {
    final var httpClientBuilder = HttpClient.newBuilder();
    jdkHttpClientProxyConfigurator.configure(httpClientBuilder);
    return new CloseableJdkHttpClientBuilder(httpClientBuilder);
  }

  /**
   * A {@link JdkHttpClientBuilder} that wraps a configured {@link HttpClient.Builder} and captures
   * the {@link HttpClient} produced when langchain4j calls {@link HttpClient.Builder#build()}.
   * Extends {@link JdkHttpClientBuilder} so it can be passed directly to langchain4j model
   * builders. Implements {@link AutoCloseable} so it can be passed directly to {@link
   * CloseableChatModelDelegate}.
   *
   * <p>All {@link HttpClient.Builder} method calls are forwarded to the real builder so any
   * configuration langchain4j applies (SSL context, version, executor, etc.) takes effect on the
   * actual client. {@link CapturingBridge#build()} captures the resulting instance for {@link
   * #close()}.
   */
  public static final class CloseableJdkHttpClientBuilder extends JdkHttpClientBuilder
      implements AutoCloseable {

    private HttpClient builtClient;

    CloseableJdkHttpClientBuilder(HttpClient.Builder delegate) {
      httpClientBuilder(new CapturingBridge(delegate));
    }

    @Override
    public void close() {
      if (builtClient != null) {
        builtClient.close();
      }
    }

    private final class CapturingBridge implements HttpClient.Builder {
      private final HttpClient.Builder delegate;

      CapturingBridge(HttpClient.Builder delegate) {
        this.delegate = delegate;
      }

      @Override
      public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
        delegate.cookieHandler(cookieHandler);
        return this;
      }

      @Override
      public HttpClient.Builder connectTimeout(Duration duration) {
        delegate.connectTimeout(duration);
        return this;
      }

      @Override
      public HttpClient.Builder sslContext(SSLContext sslContext) {
        delegate.sslContext(sslContext);
        return this;
      }

      @Override
      public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
        delegate.sslParameters(sslParameters);
        return this;
      }

      @Override
      public HttpClient.Builder executor(Executor executor) {
        delegate.executor(executor);
        return this;
      }

      @Override
      public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
        delegate.followRedirects(policy);
        return this;
      }

      @Override
      public HttpClient.Builder version(HttpClient.Version version) {
        delegate.version(version);
        return this;
      }

      @Override
      public HttpClient.Builder priority(int priority) {
        delegate.priority(priority);
        return this;
      }

      @Override
      public HttpClient.Builder proxy(ProxySelector proxySelector) {
        delegate.proxy(proxySelector);
        return this;
      }

      @Override
      public HttpClient.Builder authenticator(Authenticator authenticator) {
        delegate.authenticator(authenticator);
        return this;
      }

      @Override
      public HttpClient build() {
        return builtClient = delegate.build();
      }
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
