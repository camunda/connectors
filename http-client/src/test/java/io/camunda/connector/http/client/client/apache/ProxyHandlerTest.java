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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.client.apache.proxy.ProxyHandler;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProxyHandlerTest {

  private ProxyHandler proxyHandler;

  @BeforeEach
  public void setUp() {
    unsetAllSystemProperties();
    proxyHandler = new ProxyHandler();
  }

  @AfterAll
  public static void cleanUp() {
    unsetAllSystemProperties();
  }

  public static void unsetAllSystemProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.nonProxyHosts");
    // user and password kept to null to make tests easier. You can still test https value if needed

    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    System.clearProperty("https.proxyUser");
    System.clearProperty("https.proxyPassword");
  }

  @Test
  public void shouldThrowException_whenProxyPortInvalidInEnvVars() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST", "localhost", "CONNECTOR_HTTP_PROXY_PORT", "invalid")
              .execute(
                  () -> {
                    assertThrows(ConnectorInputException.class, () -> new ProxyHandler());
                  });
        });
  }

  @Test
  public void shouldReturnCredentialsProvider_whenConfigured() {
    System.setProperty("https.proxyHost", "localhost");
    System.setProperty("https.proxyPort", "8080");
    System.setProperty("https.proxyUser", "user");
    System.setProperty("https.proxyPassword", "password");

    ProxyHandler handler = new ProxyHandler();
    CredentialsProvider provider = handler.getCredentialsProvider("https");

    assertThat(provider).isInstanceOf(CredentialsProvider.class);
  }

  @Test
  public void shouldReturnDefaultCredentialsProvider_whenNotConfigured() {
    CredentialsProvider provider = proxyHandler.getCredentialsProvider("http");

    assertThat(provider).isInstanceOf(BasicCredentialsProvider.class);
  }

  @Test
  public void shouldNotOverwriteSystemProperties_whenProxySettingsEnvVarsAndSystemProperties()
      throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTPS_PROXY_HOST",
                  "localhost",
                  "CONNECTOR_HTTPS_PROXY_PORT",
                  "3128",
                  "CONNECTOR_HTTPS_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTPS_PROXY_PASSWORD",
                  "demo",
                  "CONNECTOR_HTTP_PROXY_NON_PROXY_HOSTS",
                  "www.env-var.de")
              .execute(
                  () -> {
                    System.setProperty("https.proxyHost", "localhost");
                    System.setProperty("https.proxyPort", "8080");
                    System.setProperty("https.proxyUser", "user");
                    System.setProperty("https.proxyPassword", "password");
                    System.setProperty("http.nonProxyHosts", "www.system-property.de");

                    new ProxyHandler();

                    assertThat(System.getProperty("https.proxyHost")).isEqualTo("localhost");
                    assertThat(System.getProperty("https.proxyPort")).isEqualTo("8080");
                    assertThat(System.getProperty("https.proxyUser")).isEqualTo("user");
                    assertThat(System.getProperty("https.proxyPassword")).isEqualTo("password");
                    assertThat(System.getProperty("http.nonProxyHosts"))
                        .isEqualTo("www.system-property.de");
                  });
        });
  }
}
