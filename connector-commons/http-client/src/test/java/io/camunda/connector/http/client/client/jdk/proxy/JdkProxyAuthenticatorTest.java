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

import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JdkProxyAuthenticatorTest {

  @Test
  void shouldReturnCredentials_whenHttpProxyAuthConfigured() throws Exception {
    var config =
        configWith(
            "http", new ProxyDetails("http", "proxy.example.com", 8080, "proxyuser", "proxypass"));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

    assertThat(auth).isNotNull();
    assertThat(auth.getUserName()).isEqualTo("proxyuser");
    assertThat(new String(auth.getPassword())).isEqualTo("proxypass");
  }

  @Test
  void shouldReturnCredentials_whenHttpsProxyAuthConfigured() throws Exception {
    var config =
        configWith(
            "https",
            new ProxyDetails("http", "secure-proxy.example.com", 3128, "httpsuser", "httpspass"));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth = requestAuthentication(authenticator, "https", "secure-proxy.example.com", 3128);

    assertThat(auth).isNotNull();
    assertThat(auth.getUserName()).isEqualTo("httpsuser");
    assertThat(new String(auth.getPassword())).isEqualTo("httpspass");
  }

  @Test
  void shouldReturnNull_whenNoCredentialsConfigured() throws Exception {
    var config =
        configWith("http", new ProxyDetails("http", "proxy.example.com", 8080, null, null));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

    assertThat(auth).isNull();
  }

  @Test
  void shouldReturnNull_whenNoProxyConfigured() throws Exception {
    var authenticator = new JdkProxyAuthenticator(ProxyConfiguration.NONE);
    var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

    assertThat(auth).isNull();
  }

  @Test
  void shouldReturnNull_whenNotProxyRequest() throws Exception {
    var config =
        configWith(
            "http", new ProxyDetails("http", "proxy.example.com", 8080, "proxyuser", "proxypass"));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth =
        requestAuthentication(
            authenticator, "http", "proxy.example.com", 8080, Authenticator.RequestorType.SERVER);

    assertThat(auth).isNull();
  }

  @Test
  void shouldHandleProtocolWithVersion() throws Exception {
    var config =
        configWith(
            "http", new ProxyDetails("http", "proxy.example.com", 8080, "proxyuser", "proxypass"));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth = requestAuthentication(authenticator, "http/1.1", "proxy.example.com", 8080);

    assertThat(auth).isNotNull();
    assertThat(auth.getUserName()).isEqualTo("proxyuser");
  }

  @Test
  void shouldReturnNull_whenProtocolDoesNotMatch() throws Exception {
    var config =
        configWith("https", new ProxyDetails("http", "proxy.example.com", 3128, "user", "pass"));
    var authenticator = new JdkProxyAuthenticator(config);
    var auth = requestAuthentication(authenticator, "http", "proxy.example.com", 8080);

    assertThat(auth).isNull();
  }

  private static ProxyConfiguration configWith(String protocol, ProxyDetails details) {
    return p -> p.equals(protocol) ? Optional.of(details) : Optional.empty();
  }

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
