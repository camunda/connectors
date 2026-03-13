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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EnvironmentProxyConfigurationTest {

  @ParameterizedTest
  @MethodSource("protocolAndPlainFlag")
  void shouldReadAllProperties(String protocol, boolean plain) throws Exception {
    String prefix = prefix(protocol, plain);
    withEnvironmentVariables(
            prefix + "HOST", "proxy.example.com",
            prefix + "PORT", "8080",
            prefix + "SCHEME", "https",
            prefix + "USER", "user",
            prefix + "PASSWORD", "pass")
        .execute(
            () -> {
              var details = createConfig(plain).getProxyDetails(protocol);

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
  @MethodSource("protocolAndPlainFlag")
  void shouldDefaultSchemeToHttp(String protocol, boolean plain) throws Exception {
    String prefix = prefix(protocol, plain);
    withEnvironmentVariables(
            prefix + "HOST", "proxy.example.com",
            prefix + "PORT", "8080")
        .execute(
            () -> {
              var details = createConfig(plain).getProxyDetails(protocol);

              assertThat(details)
                  .isPresent()
                  .hasValueSatisfying(d -> assertThat(d.scheme()).isEqualTo("http"));
            });
  }

  @ParameterizedTest
  @MethodSource("protocolAndPlainFlag")
  void shouldReturnEmpty_whenHostSetButPortMissing(String protocol, boolean plain)
      throws Exception {
    String prefix = prefix(protocol, plain);
    withEnvironmentVariables(prefix + "HOST", "proxy.example.com")
        .execute(
            () -> {
              var details = createConfig(plain).getProxyDetails(protocol);
              assertThat(details).isEmpty();
            });
  }

  @ParameterizedTest
  @MethodSource("protocolAndPlainFlag")
  void shouldReturnEmpty_whenPortSetButHostMissing(String protocol, boolean plain)
      throws Exception {
    String prefix = prefix(protocol, plain);
    withEnvironmentVariables(prefix + "PORT", "8080")
        .execute(
            () -> {
              var details = createConfig(plain).getProxyDetails(protocol);
              assertThat(details).isEmpty();
            });
  }

  @ParameterizedTest
  @MethodSource("protocolAndPlainFlag")
  void shouldThrow_whenPortIsNotANumber(String protocol, boolean plain) throws Exception {
    String prefix = prefix(protocol, plain);
    withEnvironmentVariables(
            prefix + "HOST", "proxy.example.com",
            prefix + "PORT", "not-a-number")
        .execute(
            () ->
                assertThatThrownBy(() -> createConfig(plain).getProxyDetails(protocol))
                    .isInstanceOf(ConnectorInputException.class)
                    .hasMessageContaining(prefix + "PORT"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void shouldIgnorePlainVars_whenSupportPlainProxyVarsIsFalse(String protocol) throws Exception {
    String plainPrefix = prefix(protocol, true);
    String standardPrefix = prefix(protocol, false);
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090",
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details = EnvironmentProxyConfiguration.withDefaults().getProxyDetails(protocol);

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
    String plainPrefix = prefix(protocol, true);
    String standardPrefix = prefix(protocol, false);
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            plainPrefix + "PORT", "9090",
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details =
                  EnvironmentProxyConfiguration.withPlainProxySupport().getProxyDetails(protocol);

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
  void shouldFallBackToStandardVars_whenPlainHostNotSet(String protocol) throws Exception {
    String standardPrefix = prefix(protocol, false);
    withEnvironmentVariables(
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details =
                  EnvironmentProxyConfiguration.withPlainProxySupport().getProxyDetails(protocol);

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
  void shouldFallBackToStandardVars_whenPlainIsIncomplete(String protocol) throws Exception {
    String plainPrefix = prefix(protocol, true);
    String standardPrefix = prefix(protocol, false);
    withEnvironmentVariables(
            plainPrefix + "HOST", "plain.example.com",
            standardPrefix + "HOST", "standard.example.com",
            standardPrefix + "PORT", "8080")
        .execute(
            () -> {
              var details =
                  EnvironmentProxyConfiguration.withPlainProxySupport().getProxyDetails(protocol);

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
              var config = EnvironmentProxyConfiguration.withPlainProxySupport();

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

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 65536})
  void shouldThrow_whenPortIsOutOfRange(int port) throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST",
            "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT",
            String.valueOf(port))
        .execute(
            () ->
                assertThatThrownBy(
                        () -> EnvironmentProxyConfiguration.withDefaults().getProxyDetails("http"))
                    .isInstanceOf(ConnectorInputException.class)
                    .hasMessageContaining("out of range"));
  }

  @Test
  void shouldReturnEmpty_whenNoEnvVarsSet() throws Exception {
    withEnvironmentVariables()
        .execute(
            () -> {
              var config = EnvironmentProxyConfiguration.withDefaults();
              assertThat(config.getProxyDetails("http")).isEmpty();
              assertThat(config.getProxyDetails("https")).isEmpty();
            });
  }

  @Test
  void shouldReturnEmpty_whenNeitherPlainNorStandardSet() throws Exception {
    withEnvironmentVariables()
        .execute(
            () -> {
              var config = EnvironmentProxyConfiguration.withPlainProxySupport();
              assertThat(config.getProxyDetails("http")).isEmpty();
              assertThat(config.getProxyDetails("https")).isEmpty();
            });
  }

  @Test
  void shouldNormalizeUppercaseProtocol() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              var config = EnvironmentProxyConfiguration.withDefaults();
              assertThat(config.getProxyDetails("HTTP"))
                  .isPresent()
                  .hasValueSatisfying(d -> assertThat(d.host()).isEqualTo("proxy.example.com"));
              assertThat(config.getProxyDetails("HTTPS")).isEmpty();
            });
  }

  @Test
  void shouldNormalizeVersionedProtocol() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080",
            "CONNECTOR_HTTPS_PROXY_HOST", "secure.example.com",
            "CONNECTOR_HTTPS_PROXY_PORT", "8443")
        .execute(
            () -> {
              var config = EnvironmentProxyConfiguration.withDefaults();

              assertThat(config.getProxyDetails("HTTP/1.1"))
                  .isPresent()
                  .hasValueSatisfying(d -> assertThat(d.host()).isEqualTo("proxy.example.com"));

              assertThat(config.getProxyDetails("http/1.1"))
                  .isPresent()
                  .hasValueSatisfying(d -> assertThat(d.host()).isEqualTo("proxy.example.com"));
            });
  }

  @Test
  void shouldReturnEmpty_whenProtocolIsNull() throws Exception {
    withEnvironmentVariables(
            "CONNECTOR_HTTP_PROXY_HOST", "proxy.example.com",
            "CONNECTOR_HTTP_PROXY_PORT", "8080")
        .execute(
            () -> {
              var config = EnvironmentProxyConfiguration.withDefaults();
              assertThat(config.getProxyDetails(null)).isEmpty();
            });
  }

  private static EnvironmentProxyConfiguration createConfig(boolean plain) {
    return plain
        ? EnvironmentProxyConfiguration.withPlainProxySupport()
        : EnvironmentProxyConfiguration.withDefaults();
  }

  private static String prefix(String protocol, boolean plain) {
    return "CONNECTOR_" + protocol.toUpperCase() + (plain ? "_PLAIN_PROXY_" : "_PROXY_");
  }

  static Stream<Arguments> protocolAndPlainFlag() {
    return Stream.of(
        Arguments.of("http", false),
        Arguments.of("http", true),
        Arguments.of("https", false),
        Arguments.of("https", true));
  }
}
