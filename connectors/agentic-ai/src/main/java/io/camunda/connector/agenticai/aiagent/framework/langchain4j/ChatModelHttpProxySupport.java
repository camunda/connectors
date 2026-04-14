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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
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

  JdkHttpClientBuilder createJdkHttpClientBuilder() {
    final var httpClientBuilder = HttpClient.newBuilder();
    jdkHttpClientProxyConfigurator.configure(httpClientBuilder);
    return new JdkHttpClientBuilder().httpClientBuilder(httpClientBuilder);
  }

  public SdkHttpClient createAwsHttpClient(URI endpointOverride) {
    String schemeName =
        endpointOverride != null ? endpointOverride.getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    return ApacheHttpClient.builder()
        .proxyConfiguration(createAwsProxyConfiguration(schemeName))
        .build();
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

  Optional<ProxyOptions> createAzureProxyOptions(String endpoint) {
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
