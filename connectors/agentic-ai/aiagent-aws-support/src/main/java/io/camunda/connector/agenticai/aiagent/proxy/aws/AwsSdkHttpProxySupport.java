/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.proxy.aws;

import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

/**
 * Provides HTTP clients for AWS SDK based clients (e.g. Bedrock, Bedrock AgentCore) that route
 * their requests through the configured HTTP proxy.
 */
public class AwsSdkHttpProxySupport {
  private static final Logger LOG = LoggerFactory.getLogger(AwsSdkHttpProxySupport.class);

  private final ProxyConfiguration proxyConfiguration;

  public AwsSdkHttpProxySupport(ProxyConfiguration proxyConfiguration) {
    this.proxyConfiguration = proxyConfiguration;
  }

  public ApacheHttpClient.Builder httpClientBuilder(@Nullable URI endpointOverride) {
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
   * Async equivalent of {@link #httpClientBuilder(URI)} for AWS SDK clients backed by the Netty
   * HTTP transport (required for streaming operations, which the synchronous Apache-based client
   * above cannot serve — e.g. {@code BedrockRuntimeAsyncClient.converseStream}).
   */
  public NettyNioAsyncHttpClient.Builder asyncHttpClientBuilder(@Nullable URI endpointOverride) {
    String schemeName =
        endpointOverride != null ? endpointOverride.getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    return NettyNioAsyncHttpClient.builder()
        .proxyConfiguration(awsAsyncProxyConfiguration(schemeName));
  }

  /**
   * Mechanical port of {@link #awsProxyConfiguration(String)}: {@link
   * software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder} is field-for-field identical
   * to the Apache {@code ProxyConfiguration.Builder} above ({@code host}, {@code port}, {@code
   * scheme}, {@code nonProxyHosts}, {@code username}, {@code password}, {@code
   * useSystemPropertyValues}), except that host and port are set independently rather than via a
   * single proxy endpoint URI.
   */
  software.amazon.awssdk.http.nio.netty.ProxyConfiguration awsAsyncProxyConfiguration(
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
