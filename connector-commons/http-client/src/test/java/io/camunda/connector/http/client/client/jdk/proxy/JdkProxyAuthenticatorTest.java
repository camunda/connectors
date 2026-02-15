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
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import org.junit.jupiter.api.Test;

public class JdkProxyAuthenticatorTest {

  @Test
  void shouldReturnCredentials_whenProxyAuthConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_PROXY_USER", "proxyuser",
            "CONNECTOR_HTTP_PROXY_PASSWORD", "proxypass")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var authenticator = new JdkProxyAuthenticator(config);
              var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

              assertThat(auth).isNotNull();
              assertThat(auth.getUserName()).isEqualTo("proxyuser");
              assertThat(new String(auth.getPassword())).isEqualTo("proxypass");
            });
  }

  @Test
  void shouldReturnNull_whenNoCredentialsConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var authenticator = new JdkProxyAuthenticator(config);
              var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

              assertThat(auth).isNull();
            });
  }

  @Test
  void shouldReturnNull_whenNotProxyRequest() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_PROXY_USER", "proxyuser",
            "CONNECTOR_HTTP_PROXY_PASSWORD", "proxypass")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var authenticator = new JdkProxyAuthenticator(config);
              var auth =
                  requestAuthentication(
                      authenticator,
                      "http",
                      "proxy.example.com",
                      8080,
                      Authenticator.RequestorType.SERVER);

              assertThat(auth).isNull();
            });
  }

  @Test
  void shouldReturnHttpsCredentials_forHttpsProtocol() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTPS_PROXY_HOST", "secure-proxy.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "3128",
            "CONNECTOR_HTTPS_PROXY_USER", "httpsuser",
            "CONNECTOR_HTTPS_PROXY_PASSWORD", "httpspass")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var authenticator = new JdkProxyAuthenticator(config);
              var auth =
                  requestAuthentication(authenticator, "https", "secure-proxy.example.com", 3128);

              assertThat(auth).isNotNull();
              assertThat(auth.getUserName()).isEqualTo("httpsuser");
              assertThat(new String(auth.getPassword())).isEqualTo("httpspass");
            });
  }

  @Test
  void shouldHandleProtocolWithVersion() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_PROXY_USER", "proxyuser",
            "CONNECTOR_HTTP_PROXY_PASSWORD", "proxypass")
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              var authenticator = new JdkProxyAuthenticator(config);
              // JDK Authenticator may return protocol with version like "http/1.1"
              var auth =
                  requestAuthentication(authenticator, "http/1.1", "proxy.example.com", 8080);

              assertThat(auth).isNotNull();
              assertThat(auth.getUserName()).isEqualTo("proxyuser");
            });
  }

  @Test
  void shouldNormalizeProtocol() {
    assertThat(ProtocolNormalizer.normalize("http")).isEqualTo("http");
    assertThat(ProtocolNormalizer.normalize("https")).isEqualTo("https");
    assertThat(ProtocolNormalizer.normalize("http/1.1")).isEqualTo("http");
    assertThat(ProtocolNormalizer.normalize("HTTP/1.1")).isEqualTo("http");
    assertThat(ProtocolNormalizer.normalize("HTTPS")).isEqualTo("https");
    assertThat(ProtocolNormalizer.normalize(null)).isNull();
  }

  /**
   * Simulates the JDK calling getPasswordAuthentication() on the Authenticator by using the
   * package-private requestPasswordAuthenticationInstance method via
   * Authenticator.requestPasswordAuthentication.
   */
  private PasswordAuthentication requestAuthentication(
      Authenticator authenticator, String protocol, String host, int port) throws Exception {
    return requestAuthentication(
        authenticator, protocol, host, port, Authenticator.RequestorType.PROXY);
  }

  private PasswordAuthentication requestAuthentication(
      Authenticator authenticator,
      String protocol,
      String host,
      int port,
      Authenticator.RequestorType requestorType)
      throws Exception {
    return authenticator.requestPasswordAuthenticationInstance(
        host,
        InetAddress.getByName("127.0.0.1"),
        port,
        protocol,
        "Proxy Authentication Required",
        null,
        null,
        requestorType);
  }
}
