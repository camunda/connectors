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
package io.camunda.connector.http.client.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProxyConfigurationTest {

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldReadStandardProxy(String protocol) throws Exception {
    String prefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";
    withEnvironmentVariables(
            prefix + "HOST", "proxy.example.com",
            prefix + "PORT", "8080",
            prefix + "SCHEME", "https",
            prefix + "USER", "user",
            prefix + "PASSWORD", "pass")
        .execute(
            () -> {
              var details = new ProxyConfiguration().getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(
                      d -> {
                        assertThat(d.host()).isEqualTo("proxy.example.com");
                        assertThat(d.port()).isEqualTo(8080);
                        assertThat(d.scheme()).isEqualTo("https");
                        assertThat(d.user()).isEqualTo("user");
                        assertThat(d.password()).isEqualTo("pass");
                        assertThat(d.hasCredentials()).isTrue();
                      });
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldIgnorePlainVars_whenSupportPlainProxyVarsIsFalse(String protocol) throws Exception {
    String plainPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PLAIN_PROXY_";
    String standardPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090",
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details = new ProxyConfiguration(false).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(
                      d -> {
                        assertThat(d.host()).isEqualTo("standard.example.com");
                        assertThat(d.port()).isEqualTo(8080);
                      });
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldPreferPlainVars_overStandardVars(String protocol) throws Exception {
    String plainPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PLAIN_PROXY_";
    String standardPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090",
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details = new ProxyConfiguration(true).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(
                      d -> {
                        assertThat(d.host()).isEqualTo("plain.example.com");
                        assertThat(d.port()).isEqualTo(9090);
                      });
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldReadAllPlainSubVars_includingCredentials(String protocol) throws Exception {
    String plainPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PLAIN_PROXY_";
    withEnvironmentVariables(
            plainPrefix + "SCHEME", "https",
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090",
            plainPrefix + "USER", "plainuser",
            plainPrefix + "PASSWORD", "plainpass")
        .execute(
            () -> {
              var details = new ProxyConfiguration(true).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(
                      d -> {
                        assertThat(d.scheme()).isEqualTo("https");
                        assertThat(d.host()).isEqualTo("plain.example.com");
                        assertThat(d.port()).isEqualTo(9090);
                        assertThat(d.user()).isEqualTo("plainuser");
                        assertThat(d.password()).isEqualTo("plainpass");
                        assertThat(d.hasCredentials()).isTrue();
                      });
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldDefaultPlainSchemeToHttp(String protocol) throws Exception {
    String plainPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PLAIN_PROXY_";
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090")
        .execute(
            () -> {
              var details = new ProxyConfiguration(true).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(d -> assertThat(d.scheme()).isEqualTo("http"));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldFallBackToStandardVars_whenPlainHostNotSet(String protocol) throws Exception {
    String standardPrefix = "CONNECTOR_" + protocol.toUpperCase() + "_PROXY_";
    withEnvironmentVariables(
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details = new ProxyConfiguration(true).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(
                      d -> {
                        assertThat(d.host()).isEqualTo("standard.example.com");
                        assertThat(d.port()).isEqualTo(8080);
                      });
            });
  }

  @Test
  void shouldResolvePlainAndStandardIndependentlyPerProtocol() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PLAIN_PROXY_HOST", "plain-http.example.com",
            "CONNECTOR_HTTP_PLAIN_PROXY_PORT", "9090",
            "CONNECTOR_HTTPS_PROXY_HOST", "standard-https.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "3128")
        .execute(
            () -> {
              var config = new ProxyConfiguration(true);

              assertThat(config.getProxyDetails("http"))
                  .isPresent()
                  .hasValueSatisfying(
                      d -> assertThat(d.host()).isEqualTo("plain-http.example.com"));

              assertThat(config.getProxyDetails("https"))
                  .isPresent()
                  .hasValueSatisfying(
                      d -> assertThat(d.host()).isEqualTo("standard-https.example.com"));
            });
  }

  @Test
  void shouldReturnEmpty_whenNoEnvVarsSet() throws Exception {
    withEnvironmentVariables()
        .execute(
            () -> {
              var config = new ProxyConfiguration();
              assertThat(config.getProxyDetails("http")).isEmpty();
              assertThat(config.getProxyDetails("https")).isEmpty();
            });
  }

  @Test
  void shouldReturnEmpty_whenNeitherPlainNorStandardSet() throws Exception {
    withEnvironmentVariables()
        .execute(
            () -> {
              var config = new ProxyConfiguration(true);
              assertThat(config.getProxyDetails("http")).isEmpty();
              assertThat(config.getProxyDetails("https")).isEmpty();
            });
  }
}
