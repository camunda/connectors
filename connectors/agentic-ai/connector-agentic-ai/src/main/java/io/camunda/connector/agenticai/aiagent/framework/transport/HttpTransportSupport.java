/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.transport;

import com.azure.core.http.ProxyOptions;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * Provider-neutral HTTP transport/proxy support, shared by the LangChain4j bridge and (in the
 * future) native provider clients.
 *
 * <p>Builds proxy-configured JDK {@link HttpClient} instances/builders, AWS Apache HTTP client
 * builders, and Azure {@link ProxyOptions} from a shared {@link ProxyConfiguration}, without
 * depending on any specific LLM SDK (in particular, without any {@code dev.langchain4j.*}
 * dependency). Framework-specific bridges (e.g. the LangChain4j {@code ChatModelHttpProxySupport})
 * delegate to this class and layer their own framework-specific wrapping on top where needed.
 */
public class HttpTransportSupport {
  private static final Logger LOG = LoggerFactory.getLogger(HttpTransportSupport.class);

  private final ProxyConfiguration proxyConfiguration;
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public HttpTransportSupport(
      ProxyConfiguration proxyConfiguration,
      JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator) {
    this.proxyConfiguration = proxyConfiguration;
    this.jdkHttpClientProxyConfigurator = jdkHttpClientProxyConfigurator;
  }

  /**
   * Returns a proxy-configured, not-yet-built JDK {@link HttpClient.Builder}. Use this when further
   * configuration (timeouts, SSL context, executor, etc.) needs to be applied before the client is
   * built -- e.g. by a framework bridge that hands the builder to a third-party SDK.
   */
  public HttpClient.Builder jdkHttpClientBuilder() {
    final var httpClientBuilder = HttpClient.newBuilder();
    jdkHttpClientProxyConfigurator.configure(httpClientBuilder);
    return httpClientBuilder;
  }

  /**
   * Returns a ready-to-use, proxy-configured JDK {@link HttpClient}. Equivalent to {@code
   * jdkHttpClientBuilder().build()}.
   */
  public HttpClient jdkHttpClient() {
    return jdkHttpClientBuilder().build();
  }

  /**
   * Returns a proxy-configured AWS Apache HTTP client builder for the given endpoint. Falls back to
   * the HTTPS scheme when no endpoint override is specified.
   */
  public ApacheHttpClient.Builder awsHttpClientBuilder(@Nullable URI endpointOverride) {
    String schemeName =
        endpointOverride != null ? endpointOverride.getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    return ApacheHttpClient.builder().proxyConfiguration(awsProxyConfiguration(schemeName));
  }

  software.amazon.awssdk.http.apache.ProxyConfiguration awsProxyConfiguration(String schemeName) {
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

  /**
   * Returns Azure SDK proxy options for the given endpoint, if a proxy is configured for its
   * scheme. If connector proxy env vars are not present, the Azure client will fall back to system
   * properties for proxy configuration.
   */
  public Optional<ProxyOptions> azureProxyOptions(String endpoint) {
    final var uri = URI.create(endpoint);
    if (uri.getScheme() == null) {
      throw new IllegalArgumentException("Invalid endpoint URI: " + endpoint);
    }

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

  /**
   * Provider-neutral proxy resolution for OkHttp-based vendor SDKs (anthropic-java, openai-java),
   * which accept a {@link Proxy} rather than a pre-built HTTP client. Returns the proxy configured
   * for the target scheme, if any, together with any credentials for the SDK's own proxy
   * authenticator. Shared design so C8's OpenAI native reuses it unchanged.
   */
  public Optional<OkHttpProxy> okHttpProxy(String scheme) {
    return proxyConfiguration
        .getProxyDetails(scheme)
        .map(
            proxyDetails -> {
              LOG.debug(
                  "Using proxy for target scheme [{}] => [{}:{}]",
                  scheme,
                  proxyDetails.host(),
                  proxyDetails.port());
              final var proxy =
                  new Proxy(
                      Proxy.Type.HTTP,
                      new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
              return proxyDetails.hasCredentials()
                  ? new OkHttpProxy(proxy, proxyDetails.user(), proxyDetails.password())
                  : new OkHttpProxy(proxy, null, null);
            });
  }

  /** Proxy plus optional credentials in a form neutral to any OkHttp-based SDK. */
  public record OkHttpProxy(Proxy proxy, @Nullable String username, @Nullable String password) {
    public boolean hasCredentials() {
      return username != null && !username.isBlank();
    }
  }
}
