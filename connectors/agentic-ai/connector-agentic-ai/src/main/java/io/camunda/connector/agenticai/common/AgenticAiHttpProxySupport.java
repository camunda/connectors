/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

/** Provides HTTP proxy support utilities for Agentic AI connectors. */
public class AgenticAiHttpProxySupport {
  private static final Logger LOG = LoggerFactory.getLogger(AgenticAiHttpProxySupport.class);

  private final ProxyConfiguration proxyConfiguration;
  private final JdkHttpClientProxyConfigurator jdkHttpClientProxyConfigurator;

  public AgenticAiHttpProxySupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
    this.jdkHttpClientProxyConfigurator = new JdkHttpClientProxyConfigurator(proxyConfiguration);
  }

  public ProxyConfiguration getProxyConfiguration() {
    return proxyConfiguration;
  }

  public JdkHttpClientProxyConfigurator getJdkHttpClientProxyConfigurator() {
    return jdkHttpClientProxyConfigurator;
  }

  /**
   * Returns the proxy configured for the target scheme, if any, together with any credentials for
   * an OkHttp-based SDK's own proxy authenticator (e.g. anthropic-java, openai-java, which accept a
   * {@link Proxy} rather than a pre-built HTTP client).
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

  public ApacheHttpClient.Builder createAwsHttpClientBuilder(@Nullable URI endpointOverride) {
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

  /**
   * Async equivalent of {@link #createAwsHttpClientBuilder(URI)} for AWS SDK clients backed by the
   * Netty HTTP transport (required for streaming operations, which the synchronous Apache-based
   * client above cannot serve — e.g. {@code BedrockRuntimeAsyncClient.converseStream}).
   */
  public NettyNioAsyncHttpClient.Builder createAwsAsyncHttpClientBuilder(
      @Nullable URI endpointOverride) {
    String schemeName =
        endpointOverride != null ? endpointOverride.getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    return NettyNioAsyncHttpClient.builder()
        .proxyConfiguration(createAwsAsyncProxyConfiguration(schemeName));
  }

  /**
   * Mechanical port of {@link #createAwsProxyConfiguration(String)}: {@link
   * software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder} is field-for-field identical
   * to the Apache {@code ProxyConfiguration.Builder} above ({@code host}, {@code port}, {@code
   * scheme}, {@code nonProxyHosts}, {@code username}, {@code password}, {@code
   * useSystemPropertyValues}), except that host and port are set independently rather than via a
   * single proxy endpoint URI.
   */
  software.amazon.awssdk.http.nio.netty.ProxyConfiguration createAwsAsyncProxyConfiguration(
      String schemeName) {
    software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder awsProxyConfigBuilder =
        software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
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
                  .host(proxyDetails.host())
                  .port(proxyDetails.port())
                  .nonProxyHosts(
                      NonProxyHosts.getNonProxyHostRegexPatterns().collect(Collectors.toSet()));

              if (proxyDetails.hasCredentials()) {
                awsProxyConfigBuilder.username(proxyDetails.user());
                awsProxyConfigBuilder.password(proxyDetails.password());
              }
            });

    return awsProxyConfigBuilder.build();
  }

  private static URI toUri(ProxyConfiguration.ProxyDetails proxyDetails) {
    return URI.create(
        proxyDetails.scheme() + "://" + proxyDetails.host() + ":" + proxyDetails.port());
  }
}
