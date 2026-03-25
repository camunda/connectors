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
package io.camunda.connector.http.client.client.apache;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.client.apache.proxy.ProxyHandler;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class ProxyHandlerTest {

  @SystemStub private EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void shouldReturnProxyDetails_whenConfigured(String protocol) {
    environmentVariables
        .set(envVar(protocol, "HOST"), "proxy.example.com")
        .set(envVar(protocol, "PORT"), "3128");

    ProxyHandler handler = new ProxyHandler();

    assertThat(handler.getProxyDetails(protocol))
        .isPresent()
        .hasValueSatisfying(
            d -> {
              assertThat(d.host()).isEqualTo("proxy.example.com");
              assertThat(d.port()).isEqualTo(3128);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void shouldReturnEmpty_whenNoVarsConfigured(String protocol) {
    ProxyHandler handler = new ProxyHandler();

    assertThat(handler.getProxyDetails(protocol)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void shouldReturnCredentialsProvider_withCorrectCredentials(String protocol) {
    environmentVariables
        .set(envVar(protocol, "HOST"), "proxy.example.com")
        .set(envVar(protocol, "PORT"), "3128")
        .set(envVar(protocol, "USER"), "myuser")
        .set(envVar(protocol, "PASSWORD"), "mypass");

    ProxyHandler handler = new ProxyHandler();

    assertThat(
            handler
                .getCredentialsProvider(protocol)
                .getCredentials(new AuthScope("proxy.example.com", 3128), null))
        .isNotNull()
        .satisfies(
            creds -> {
              assertThat(creds.getUserPrincipal().getName()).isEqualTo("myuser");
              assertThat(new String(creds.getPassword())).isEqualTo("mypass");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void shouldReturnEmptyCredentialsProvider_whenNoCredentials(String protocol) {
    environmentVariables
        .set(envVar(protocol, "HOST"), "proxy.example.com")
        .set(envVar(protocol, "PORT"), "3128");

    ProxyHandler handler = new ProxyHandler();

    assertThat(
            handler
                .getCredentialsProvider(protocol)
                .getCredentials(new AuthScope("proxy.example.com", 3128), null))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  public void shouldReturnEmptyCredentialsProvider_whenNoProxyConfigured(String protocol) {
    ProxyHandler handler = new ProxyHandler();

    // returns a credentials provider
    CredentialsProvider credentialsProvider = handler.getCredentialsProvider(protocol);
    assertThat(credentialsProvider).isNotNull().isInstanceOf(BasicCredentialsProvider.class);

    // wildcard auth scope does not return anything
    assertThat(credentialsProvider.getCredentials(new AuthScope(null, -1), null)).isNull();
  }

  @Test
  public void shouldNeverConsumePlainProxyVars() {
    environmentVariables
        .set("CONNECTOR_HTTP_PLAIN_PROXY_HOST", "plain-proxy.example.com")
        .set("CONNECTOR_HTTP_PLAIN_PROXY_PORT", "9090")
        .set("CONNECTOR_HTTP_PLAIN_PROXY_USER", "plainuser")
        .set("CONNECTOR_HTTP_PLAIN_PROXY_PASSWORD", "plainpass")
        .set("CONNECTOR_HTTPS_PLAIN_PROXY_HOST", "plain-secure.example.com")
        .set("CONNECTOR_HTTPS_PLAIN_PROXY_PORT", "3129");

    ProxyHandler handler = new ProxyHandler();

    assertThat(handler.getProxyDetails("http")).isEmpty();
    assertThat(handler.getProxyDetails("https")).isEmpty();
  }

  @Test
  public void shouldUseStandardVars_evenWhenPlainVarsAlsoSet() {
    environmentVariables
        .set("CONNECTOR_HTTP_PLAIN_PROXY_HOST", "plain-proxy.example.com")
        .set("CONNECTOR_HTTP_PLAIN_PROXY_PORT", "9090")
        .set("CONNECTOR_HTTP_PROXY_HOST", "standard-proxy.example.com")
        .set("CONNECTOR_HTTP_PROXY_PORT", "8080");

    ProxyHandler handler = new ProxyHandler();

    assertThat(handler.getProxyDetails("http"))
        .isPresent()
        .hasValueSatisfying(
            d -> {
              assertThat(d.host()).isEqualTo("standard-proxy.example.com");
              assertThat(d.port()).isEqualTo(8080);
            });
  }

  private String envVar(String protocol, String suffix) {
    return "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_" + suffix;
  }
}
