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
import java.net.ProxySelector;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JdkHttpClientProxyConfiguratorTest {

  @Test
  void shouldNotSetProxyOrAuthenticator_whenNoProxyConfigured() {
    var client = JdkHttpClientProxyConfigurator.newHttpClient(ProxyConfiguration.NONE);
    assertThat(client.proxy()).isEmpty();
    assertThat(client.authenticator()).isEmpty();
  }

  @Test
  void shouldSetProxyAndAuthenticator_whenHttpProxyConfigured() {
    var config =
        configWith("http", new ProxyDetails("http", "proxy.example.com", 8080, "user", "pass"));
    var client = JdkHttpClientProxyConfigurator.newHttpClient(config);
    assertThat(client.proxy()).isPresent();
    assertThat(client.proxy().get()).isInstanceOf(ProxySelector.class);
    assertThat(client.authenticator()).isPresent();
    assertThat(client.authenticator().get()).isInstanceOf(Authenticator.class);
  }

  @Test
  void shouldSetProxy_whenHttpsProxyConfigured() {
    var config =
        configWith("https", new ProxyDetails("http", "secure-proxy.example.com", 3128, null, null));
    var client = JdkHttpClientProxyConfigurator.newHttpClient(config);
    assertThat(client.proxy()).isPresent();
    assertThat(client.proxy().get()).isInstanceOf(JdkProxySelector.class);
  }

  @Test
  void shouldCreateClientViaInstance() {
    var config =
        configWith("http", new ProxyDetails("http", "proxy.example.com", 8080, null, null));
    var proxy = new JdkHttpClientProxyConfigurator(config);
    var client = proxy.newHttpClient();
    assertThat(client.proxy()).isPresent();
  }

  private static ProxyConfiguration configWith(String protocol, ProxyDetails details) {
    return p -> p.equals(protocol) ? Optional.of(details) : Optional.empty();
  }
}
