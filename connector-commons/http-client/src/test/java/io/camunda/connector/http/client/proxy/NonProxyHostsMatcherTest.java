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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class NonProxyHostsMatcherTest {

  @AfterEach
  public void clearSystemProperties() {
    System.clearProperty("http.nonProxyHosts");
  }

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
        Arguments.of("localhost", "example.com", false));
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  void shouldMatchNonProxyHosts_fromSystemProperty(
      String nonProxyHosts, String hostname, boolean expectedMatch) {
    System.setProperty("http.nonProxyHosts", nonProxyHosts);
    assertThat(NonProxyHostsMatcher.isNonProxyHost(hostname)).isEqualTo(expectedMatch);
  }

  @ParameterizedTest
  @MethodSource("provideNonProxyHostTestData")
  void shouldMatchNonProxyHosts_fromEnvVar(
      String nonProxyHosts, String hostname, boolean expectedMatch) throws Exception {
    uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables(
            "CONNECTOR_HTTP_NON_PROXY_HOSTS", nonProxyHosts)
        .execute(
            () ->
                assertThat(NonProxyHostsMatcher.isNonProxyHost(hostname)).isEqualTo(expectedMatch));
  }
}
