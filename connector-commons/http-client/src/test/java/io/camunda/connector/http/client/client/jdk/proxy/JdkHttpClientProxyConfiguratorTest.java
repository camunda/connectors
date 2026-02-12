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
import java.net.ProxySelector;
import org.junit.jupiter.api.Test;

public class JdkHttpClientProxyConfiguratorTest {

  @Test
  void shouldNotSetProxyOrAuthenticator_whenNoEnvVarsConfigured() {
    var client = JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
    assertThat(client.proxy()).isEmpty();
    assertThat(client.authenticator()).isEmpty();
  }

  @Test
  void shouldSetProxyAndAuthenticator_whenHttpProxyConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTP_PROXY_USER", "user",
            "CONNECTOR_HTTP_PROXY_PASSWORD", "pass")
        .execute(
            () -> {
              var client = JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
              assertThat(client.proxy()).isPresent();
              assertThat(client.proxy().get()).isInstanceOf(ProxySelector.class);
              assertThat(client.authenticator()).isPresent();
              assertThat(client.authenticator().get()).isInstanceOf(Authenticator.class);
            });
  }

  @Test
  void shouldSetProxyAndAuthenticator_whenHttpsProxyConfigured() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTPS_PROXY_HOST", "secure-proxy.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "3128")
        .execute(
            () -> {
              var client = JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
              assertThat(client.proxy()).isPresent();
              assertThat(client.proxy().get()).isInstanceOf(JdkProxySelector.class);
            });
  }

  @Test
  void shouldCreateClientViaInstance() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              var proxy = new JdkHttpClientProxyConfigurator(new ProxyConfiguration());
              var client = proxy.newHttpClient();
              assertThat(client.proxy()).isPresent();
            });
  }
}
