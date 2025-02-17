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
package io.camunda.connector.http.base.client.apache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.stream.Stream;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProxyHandlerTest {

  private ProxyHandler proxyHandler;

  @BeforeEach
  public void setUp() {
    System.setProperty("http.proxyHost", "");
    System.setProperty("http.proxyPort", "");
    System.setProperty("http.proxyUser", "");
    System.setProperty("http.proxyPassword", "");
    System.setProperty("https.proxyHost", "");
    System.setProperty("https.proxyPort", "");
    System.setProperty("https.proxyUser", "");
    System.setProperty("https.proxyPassword", "");
    proxyHandler = new ProxyHandler();
  }

  @Nested
  class LoadProxyConfigTests {
    @AfterEach
    public void unsetAllSystemProperties() {
      System.setProperty("http.proxyHost", "");
      System.setProperty("http.proxyPort", "");
      System.setProperty("http.nonProxyHosts", "");
      System.setProperty("https.proxyHost", "");
      System.setProperty("https.proxyPort", "");
      System.setProperty("https.nonProxyHosts", "");
      System.setProperty("http.proxyUser", "");
      System.setProperty("http.proxyPassword", "");
    }

    @Test
    public void shouldUseCorrectProtocol_whenMultipleProtocolsSet() {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "8080");
      System.setProperty("https.proxyHost", "localhost");
      System.setProperty("https.proxyPort", "8081");

      ProxyHandler handler = new ProxyHandler();
      HttpHost httpProxyHost = handler.getProxyHost("http", "http://example.com");
      HttpHost httpsProxyHost = handler.getProxyHost("https", "https://example.com");

      assertThat(httpProxyHost.getPort()).isEqualTo(8080);
      assertThat(httpsProxyHost.getPort()).isEqualTo(8081);
    }

    @Test
    public void shouldThrowException_whenProxyPortInvalidInSystemProperties() {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "invalid");

      assertThrows(ConnectorInputException.class, () -> new ProxyHandler());
    }
  }

  @Nested
  class GetProxyHostTests {

    @Test
    public void shouldReturnNull_whenNoProxyConfiguredForProtocol() {
      System.setProperty("https.proxyHost", "localhost");
      System.setProperty("https.proxyPort", "8080");
      HttpHost proxyHost = proxyHandler.getProxyHost("http", "http://example.com");

      assertThat(proxyHost).isNull();
    }

    @Test
    public void shouldReturnProxyHost_whenConfigured() {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "8080");

      ProxyHandler handler = new ProxyHandler();
      HttpHost proxyHost = handler.getProxyHost("http", "http://example.com");

      assertThat(proxyHost).isNotNull();
      assertThat(proxyHost.getHostName()).isEqualTo("localhost");
      assertThat(proxyHost.getPort()).isEqualTo(8080);
    }

    private static Stream<Arguments> provideNonProxyHostTestData() {
      return Stream.of(
          Arguments.of("www.example.de", "www.example.de", true),
          Arguments.of("www.camunda.de", "www.camunda.de", true),
          Arguments.of("www.example.de", "www.camunda.de", false),
          Arguments.of("www.example.de|www.camunda.de", "www.camunda.io", false),
          Arguments.of("example.de", "www.example.de", true),
          Arguments.of("example.de", "api.example.de", true),
          Arguments.of("example.de", "another.example.de", true),
          Arguments.of("example.de", "example.com", false),
          Arguments.of("example.de|camunda.de", "www.example.de", true),
          Arguments.of("example.de|camunda.de", "www.camunda.de", true),
          Arguments.of("example.de|camunda.de", "www.google.de", false),
          Arguments.of("*.example.de", "api.example.de", true),
          Arguments.of("*.example.de", "www.example.de", true),
          Arguments.of("*.example.de", "example.de", false),
          Arguments.of("*.example.de", "example.com", false),
          Arguments.of("*.example.de|*.camunda.io", "sub.example.de", true),
          Arguments.of("*.example.de|*.camunda.io", "api.camunda.io", true),
          Arguments.of("*.example.de|*.camunda.io", "www.google.com", false));
    }

    @ParameterizedTest
    @MethodSource("provideNonProxyHostTestData")
    public void shouldReturnExpected_whenNonProxyHostsSet(
        String nonProxyHosts, String requestUrl, Boolean isNull) {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "8080");
      System.setProperty("http.nonProxyHosts", nonProxyHosts);

      ProxyHandler handler = new ProxyHandler();
      HttpHost proxyHost = handler.getProxyHost("http", requestUrl);

      assertThat(proxyHost).isEqualTo(isNull ? null : new HttpHost("http", "localhost", 8080));
    }
  }

  @Nested
  class GetRoutePlannerTests {

    @Test
    public void shouldReturnSystemDefaultRoutePlanner_whenConfiguredFromSystemProperties() {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "8080");

      ProxyHandler handler = new ProxyHandler();
      HttpHost proxyHost = handler.getProxyHost("http", "http://example.com");
      var routePlanner = handler.getRoutePlanner("http", proxyHost);

      assertThat(routePlanner).isInstanceOf(SystemDefaultRoutePlanner.class);
    }

    @Test
    public void shouldReturnDefaultProxyRoutePlanner_whenProxyHostProvidedFromEnvVars()
        throws Exception {
      restoreSystemProperties(
          () -> {
            withEnvironmentVariables(
                    "CONNECTOR_HTTP_PROXY_HOST",
                    "localhost",
                    "CONNECTOR_HTTP_PROXY_PORT",
                    "8080",
                    "CONNECTOR_HTTP_PROXY_USER",
                    "user",
                    "CONNECTOR_HTTP_PROXY_PASSWORD",
                    "password")
                .execute(
                    () -> {
                      HttpHost proxyHost = new HttpHost("http", "localhost", 8080);
                      var routePlanner = proxyHandler.getRoutePlanner("http", proxyHost);

                      assertThat(routePlanner).isInstanceOf(DefaultProxyRoutePlanner.class);
                    });
          });
    }

    @Test
    public void shouldReturnNullWhenNoProxyConfigured() {
      var routePlanner = proxyHandler.getRoutePlanner("http", null);
      assertThat(routePlanner).isNull();
    }
  }

  @Nested
  class GetCredentialsProviderTests {

    @Test
    public void shouldReturnCredentialsProvider_whenConfigured() {
      System.setProperty("http.proxyHost", "localhost");
      System.setProperty("http.proxyPort", "8080");
      System.setProperty("http.proxyUser", "user");
      System.setProperty("http.proxyPassword", "password");

      ProxyHandler handler = new ProxyHandler();
      CredentialsProvider provider = handler.getCredentialsProvider("http");

      assertThat(provider).isInstanceOf(CredentialsProvider.class);
    }

    @Test
    public void shouldReturnDefaultCredentialsProvider_whenNotConfigured() {
      CredentialsProvider provider = proxyHandler.getCredentialsProvider("http");

      assertThat(provider).isInstanceOf(BasicCredentialsProvider.class);
    }
  }
}
