/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.client.jdk.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.Proxy;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class JdkProxySelectorTest {

  @AfterEach
  public void clearSystemProperties() {
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  void shouldReturnNoProxy_whenNoEnvVarsConfigured() {
    var selector = new JdkProxySelector(new ProxyConfiguration());
    var proxies = selector.select(URI.create("http://example.com"));

    assertThat(proxies).hasSize(1);
    assertThat(proxies.getFirst().type()).isEqualTo(Proxy.Type.DIRECT);
  }

  @Test
  void shouldReturnProxy_whenHttpProxyConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);
              var proxies = selector.select(URI.create("http://target.com"));

              assertThat(proxies).hasSize(1);
              var proxy = proxies.getFirst();
              assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
              assertThat(proxy.address().toString()).contains("proxy.example.com");
              assertThat(proxy.address().toString()).contains("8080");
            });
  }

  @Test
  void shouldReturnProxy_whenHttpsProxyConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTPS_PROXY_HOST", "secure-proxy.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "3128")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);
              var proxies = selector.select(URI.create("https://target.com"));

              assertThat(proxies).hasSize(1);
              var proxy = proxies.getFirst();
              assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
              assertThat(proxy.address().toString()).contains("secure-proxy.example.com");
              assertThat(proxy.address().toString()).contains("3128");
            });
  }

  @Test
  void shouldReturnNoProxy_whenHttpsProxyConfiguredButHttpRequested() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTPS_PROXY_HOST", "secure-proxy.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "3128")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);
              var proxies = selector.select(URI.create("http://target.com"));

              assertThat(proxies).hasSize(1);
              assertThat(proxies.getFirst().type()).isEqualTo(Proxy.Type.DIRECT);
            });
  }

  @Test
  void shouldSkipProxy_whenHostMatchesNonProxyHosts() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_NON_PROXY_HOSTS", "*.internal.com|localhost")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);

              // Should skip proxy for matching hosts
              assertThat(selector.select(URI.create("http://api.internal.com")).getFirst().type())
                  .isEqualTo(Proxy.Type.DIRECT);
              assertThat(selector.select(URI.create("http://localhost")).getFirst().type())
                  .isEqualTo(Proxy.Type.DIRECT);

              // Should use proxy for non-matching hosts
              assertThat(selector.select(URI.create("http://external.com")).getFirst().type())
                  .isEqualTo(Proxy.Type.HTTP);
            });
  }

  @Test
  void shouldSkipProxy_whenHostMatchesNonProxyHostsSystemProperty() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              System.setProperty("http.nonProxyHosts", "*.internal.com");
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);

              assertThat(selector.select(URI.create("http://api.internal.com")).getFirst().type())
                  .isEqualTo(Proxy.Type.DIRECT);
              assertThat(selector.select(URI.create("http://external.com")).getFirst().type())
                  .isEqualTo(Proxy.Type.HTTP);
            });
  }

  @Test
  void shouldThrowException_whenUriIsNull() {
    var selector = new JdkProxySelector(new ProxyConfiguration());
    assertThatThrownBy(() -> selector.select(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("URI must not be null");
  }

  private static Stream<Arguments> provideNonProxyHostTestData() {
    return Stream.of(
        Arguments.of("*.example.de", "http://api.example.de", true),
        Arguments.of("*.example.de", "http://www.example.de", true),
        Arguments.of("*.example.de", "http://example.de", false),
        Arguments.of("*.example.de|*.camunda.io", "http://sub.example.de", true),
        Arguments.of("*.example.de|*.camunda.io", "http://api.camunda.io", true),
        Arguments.of("*.example.de|*.camunda.io", "http://www.google.com", false));
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  void shouldMatchNonProxyHostPatterns(String nonProxyHosts, String requestUrl, Boolean skipProxy)
      throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_NON_PROXY_HOSTS", nonProxyHosts)
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var selector = new JdkProxySelector(config);
              var proxies = selector.select(URI.create(requestUrl));

              if (skipProxy) {
                assertThat(proxies.getFirst().type()).isEqualTo(Proxy.Type.DIRECT);
              } else {
                assertThat(proxies.getFirst().type()).isEqualTo(Proxy.Type.HTTP);
              }
            });
  }

  @Test
  void shouldNormalizeProtocol() {
    assertThat(ProtocolNormalizer.normalize("http")).isEqualTo("http");
    assertThat(ProtocolNormalizer.normalize("https")).isEqualTo("https");
    assertThat(ProtocolNormalizer.normalize("HTTP")).isEqualTo("http");
    assertThat(ProtocolNormalizer.normalize("HTTPS")).isEqualTo("https");
    assertThat(ProtocolNormalizer.normalize(null)).isNull();
  }
}
