/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.client.jdk.proxy.JdkProxyAuthenticator;
import io.camunda.connector.http.client.client.jdk.proxy.JdkProxySelector;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgenticAiHttpProxySupportTest {

  @Test
  void shouldConfigureProxyOnHttpClientBuilder() throws Exception {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    assertThat(client.authenticator()).isPresent().get().isInstanceOf(JdkProxyAuthenticator.class);
    var auth =
        client
            .authenticator()
            .get()
            .requestPasswordAuthenticationInstance(
                "proxy.example.com",
                InetAddress.getByName("127.0.0.1"),
                8080,
                "http",
                "Proxy Authentication Required",
                null,
                null,
                Authenticator.RequestorType.PROXY);
    assertThat(auth).isNotNull();
    assertThat(auth.getUserName()).isEqualTo("user");
    assertThat(new String(auth.getPassword())).isEqualTo("pass");
  }

  @Test
  void shouldConfigureProxyWithoutCredentials() throws Exception {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 3128, null, null);
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    // Authenticator is always set when a proxy is configured, but it returns null credentials
    // when none are provided — the actual credential check is handled by JdkProxyAuthenticator
    assertThat(client.authenticator()).isPresent().get().isInstanceOf(JdkProxyAuthenticator.class);
    var auth =
        client
            .authenticator()
            .get()
            .requestPasswordAuthenticationInstance(
                "proxy.example.com",
                InetAddress.getByName("127.0.0.1"),
                3128,
                "http",
                "Proxy Authentication Required",
                null,
                null,
                Authenticator.RequestorType.PROXY);
    assertThat(auth).isNull();
  }

  @Test
  void shouldNotConfigureProxyWhenDisabled() {
    var httpProxySupport = new AgenticAiHttpProxySupport(ProxyConfiguration.NONE);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isEmpty();
    assertThat(client.authenticator()).isEmpty();
  }

  @Test
  void shouldExposeProxyConfiguration() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    assertThat(httpProxySupport.getProxyConfiguration()).isSameAs(proxyConfig);
  }

  @Test
  void shouldExposeJdkHttpClientProxyConfigurator() {
    var proxyConfig = testProxyConfiguration("proxy.example.com", 8080, "user", "pass");
    var httpProxySupport = new AgenticAiHttpProxySupport(proxyConfig);

    assertThat(httpProxySupport.getJdkHttpClientProxyConfigurator())
        .isNotNull()
        .isInstanceOf(JdkHttpClientProxyConfigurator.class);
  }

  @Test
  void shouldConfigureProxyForHttpOnly() throws Exception {
    ProxyConfiguration httpOnlyProxy =
        protocol -> {
          if (ProxyConfiguration.SCHEME_HTTP.equals(protocol)) {
            return Optional.of(
                new ProxyDetails(
                    ProxyConfiguration.SCHEME_HTTP, "proxy.example.com", 8080, null, null));
          }
          return Optional.empty();
        };
    var httpProxySupport = new AgenticAiHttpProxySupport(httpOnlyProxy);

    var builder = HttpClient.newBuilder();
    httpProxySupport.getJdkHttpClientProxyConfigurator().configure(builder);
    var client = builder.build();

    assertThat(client.proxy()).isPresent().get().isInstanceOf(JdkProxySelector.class);
    assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("http")).isPresent();
    assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("https")).isEmpty();
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
