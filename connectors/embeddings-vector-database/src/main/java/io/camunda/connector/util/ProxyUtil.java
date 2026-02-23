/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.util;

import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.util.stream.Collectors;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

public final class ProxyUtil {
  private ProxyUtil() {}

  public static URI toUri(ProxyConfiguration.ProxyDetails proxyDetails) {
    return URI.create(
        proxyDetails.scheme() + "://" + proxyDetails.host() + ":" + proxyDetails.port());
  }

  public static SdkHttpClient createAwsProxyAwareHttpClient(
      ProxyConfiguration proxyConfig, String schemeName) {
    software.amazon.awssdk.http.apache.ProxyConfiguration.Builder awsProxyConfigBuilder =
        software.amazon.awssdk.http.apache.ProxyConfiguration.builder()
            .useSystemPropertyValues(true);

    proxyConfig
        .getProxyDetails(schemeName)
        .ifPresent(
            proxyDetails -> {
              awsProxyConfigBuilder
                  .scheme(proxyDetails.scheme())
                  .endpoint(ProxyUtil.toUri(proxyDetails))
                  // the SDK does not sanitize the patterns
                  .nonProxyHosts(
                      NonProxyHosts.getNonProxyHostRegexPatterns().collect(Collectors.toSet()));

              if (proxyDetails.hasCredentials()) {
                awsProxyConfigBuilder.username(proxyDetails.user());
                awsProxyConfigBuilder.password(proxyDetails.password());
              }
            });

    return ApacheHttpClient.builder().proxyConfiguration(awsProxyConfigBuilder.build()).build();
  }
}
