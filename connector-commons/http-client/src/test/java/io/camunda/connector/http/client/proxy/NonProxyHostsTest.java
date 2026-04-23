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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class NonProxyHostsTest {

  @SystemStub
  private SystemProperties systemProperties = new SystemProperties().remove("http.nonProxyHosts");

  @SystemStub
  private EnvironmentVariables environmentVariables =
      new EnvironmentVariables().remove("CONNECTOR_HTTP_NON_PROXY_HOSTS");

  private static Stream<Arguments> provideNonProxyHostTestData() {
    return Stream.of(
        // nonProxyHosts, hostname, expectedMatch
        Arguments.of("example.de", "example.de", true),
        Arguments.of("example.de", "www.example.de", false),
        Arguments.of("www.example.de", "www.example.de", true),
        Arguments.of("example.de|www.camunda.de", "www.camunda.io", false),
        Arguments.of("example.de", "api.example.de", false),
        Arguments.of("example.de", "example.com", false),
        Arguments.of("example.de|camunda.de", "www.example.de", false),
        Arguments.of("example.de|camunda.de", "www.google.de", false),
        Arguments.of("*.example.de", "api.example.de", true),
        Arguments.of("*.example.de", "www.example.de", true),
        Arguments.of("*.example.de", "example.de", false),
        Arguments.of("*.example.de", "example.com", false),
        Arguments.of("*.example.de|*.camunda.io", "sub.example.de", true),
        Arguments.of("*.example.de|*.camunda.io", "api.camunda.io", true),
        Arguments.of("*.example.de|*.camunda.io", "www.google.com", false),
        Arguments.of("localhost", "localhost", true),
        Arguments.of("localhost", "example.com", false),
        // dots in patterns must be treated as literals, not regex wildcards
        Arguments.of("example.de", "exampleXde", false),
        Arguments.of("example.de", "example-de", false));
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  void shouldMatchNonProxyHosts_fromSystemProperty(
      String nonProxyHosts, String hostname, boolean expectedMatch) {
    System.setProperty("http.nonProxyHosts", nonProxyHosts);
    assertThat(NonProxyHosts.isNonProxyHost(hostname)).isEqualTo(expectedMatch);
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  void shouldMatchNonProxyHosts_fromEnvVar(
      String nonProxyHosts, String hostname, boolean expectedMatch) {
    environmentVariables.set("CONNECTOR_HTTP_NON_PROXY_HOSTS", nonProxyHosts);
    assertThat(NonProxyHosts.isNonProxyHost(hostname)).isEqualTo(expectedMatch);
  }

  @Test
  void shouldReturnPatternsFromSystemProperty() {
    System.setProperty("http.nonProxyHosts", "localhost|*.example.com");
    assertThat(NonProxyHosts.getNonProxyHostsPatterns()).containsExactly("localhost|*.example.com");
  }

  @Test
  void shouldReturnPatternsFromEnvVar() {
    environmentVariables.set("CONNECTOR_HTTP_NON_PROXY_HOSTS", "*.internal.com|127.0.0.1");
    assertThat(NonProxyHosts.getNonProxyHostsPatterns())
        .containsExactly("*.internal.com|127.0.0.1");
  }

  @Test
  void shouldReturnPatternsFromBothSources() {
    System.setProperty("http.nonProxyHosts", "localhost");
    environmentVariables.set("CONNECTOR_HTTP_NON_PROXY_HOSTS", "*.example.com");
    assertThat(NonProxyHosts.getNonProxyHostsPatterns())
        .containsExactlyInAnyOrder("localhost", "*.example.com");
  }

  @Test
  void shouldReturnEmptyStreamWhenNoPatternsConfigured() {
    assertThat(NonProxyHosts.getNonProxyHostsPatterns()).isEmpty();
  }

  @Test
  void shouldReturnRegexPatternsFromSystemProperty() {
    System.setProperty("http.nonProxyHosts", "localhost|*.example.com");
    assertThat(NonProxyHosts.getNonProxyHostRegexPatterns())
        .containsExactly("\\Qlocalhost\\E|\\Q\\E.*\\Q.example.com\\E");
  }

  @Test
  void shouldReturnRegexPatternsFromBothSources() {
    System.setProperty("http.nonProxyHosts", "localhost");
    environmentVariables.set("CONNECTOR_HTTP_NON_PROXY_HOSTS", "*.example.com");
    assertThat(NonProxyHosts.getNonProxyHostRegexPatterns())
        .containsExactlyInAnyOrder("\\Qlocalhost\\E", "\\Q\\E.*\\Q.example.com\\E");
  }

  @Test
  void shouldReturnEmptyStreamWhenNoRegexPatternsConfigured() {
    assertThat(NonProxyHosts.getNonProxyHostRegexPatterns()).isEmpty();
  }
}
