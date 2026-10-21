/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.proxy.azure;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AzureHttpProxySupportTest {

  private static final String NON_PROXY_HOST_LOCALHOST_REGEX = "localhost.*";
  private static final String NON_PROXY_HOST_127 = "127\\.0\\.0\\.1";

  @Test
  void proxyOptionsReturnsEmptyWhenNoProxyConfiguredForScheme() {
    var proxySupport = new AzureHttpProxySupport(ProxyConfiguration.NONE);

    assertThat(proxySupport.proxyOptions(SCHEME_HTTPS)).isEmpty();
  }

  @Test
  void proxyOptionsAppliesNonProxyHosts() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, null, null);
    var proxySupport = new AzureHttpProxySupport(proxyConfig);

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock
          .when(NonProxyHosts::getNonProxyHostsPatterns)
          .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127));

      var result = proxySupport.proxyOptions(SCHEME_HTTPS);

      assertThat(result).isPresent();
      var proxyOptions = result.get();
      assertThat(proxyOptions.getAddress())
          .isEqualTo(new InetSocketAddress("proxy.example.com", 8080));
      assertThat(proxyOptions.getNonProxyHosts()).contains("localhost").contains("127");
      assertThat(proxyOptions.getUsername()).isNull();
      assertThat(proxyOptions.getPassword()).isNull();
    }
  }

  @Test
  void proxyOptionsAppliesCredentials() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var proxySupport = new AzureHttpProxySupport(proxyConfig);

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock.when(NonProxyHosts::getNonProxyHostsPatterns).thenReturn(Stream.empty());

      var result = proxySupport.proxyOptions(SCHEME_HTTPS);

      assertThat(result).isPresent();
      var proxyOptions = result.get();
      assertThat(proxyOptions.getUsername()).isEqualTo("user");
      assertThat(proxyOptions.getPassword()).isEqualTo("pass");
    }
  }

  private static ProxyConfiguration testProxyConfiguration(
      String host, int port, String user, String password) {
    return protocol -> {
      if (ProxyConfiguration.SCHEME_HTTP.equals(protocol)
          || ProxyConfiguration.SCHEME_HTTPS.equals(protocol)) {
        return Optional.of(
            new ProxyDetails(ProxyConfiguration.SCHEME_HTTP, host, port, user, password));
      }
      return Optional.empty();
    };
  }
}
