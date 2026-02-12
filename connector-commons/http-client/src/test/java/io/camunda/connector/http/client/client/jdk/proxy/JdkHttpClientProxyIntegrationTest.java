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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.test.utils.DockerImages;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class JdkHttpClientProxyIntegrationTest {
  private static final String SQUID = "squid";

  private static final WireMockServer wireMockTarget = new WireMockServer(options().dynamicPort());
  private static GenericContainer<?> squidProxyContainer;

  @BeforeAll
  public static void setUp() {
    wireMockTarget.start();
    squidProxyContainer =
        new GenericContainer<>(DockerImageName.parse(DockerImages.get(SQUID)))
            .withExposedPorts(3128)
            .withClasspathResourceMapping("squid.conf", "/etc/squid/squid.conf", BindMode.READ_ONLY)
            .withClasspathResourceMapping("passwords", "/etc/squid/passwords", BindMode.READ_ONLY)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
    // Expose WireMock port so Squid container can reach it via host.testcontainers.internal
    Testcontainers.exposeHostPorts(wireMockTarget.port());
    squidProxyContainer.withAccessToHost(true);
    squidProxyContainer.start();
  }

  @AfterAll
  public static void tearDown() {
    wireMockTarget.stop();
    squidProxyContainer.stop();
  }

  @BeforeEach
  public void resetWireMock() {
    wireMockTarget.resetAll();
  }

  @AfterEach
  public void clearSystemProperties() {
    System.clearProperty("http.nonProxyHosts");
  }

  /**
   * Returns the WireMock URL accessible from Squid proxy container. Uses
   * host.testcontainers.internal so the Squid container can reach the WireMock server running on
   * the host machine.
   */
  private static String getWireMockUrl(String path) {
    return "http://host.testcontainers.internal:" + wireMockTarget.port() + path;
  }

  /**
   * Returns the WireMock URL accessible directly from test JVM (bypassing proxy). Uses localhost
   * since WireMock is running in the same JVM.
   */
  private static String getWireMockUrlDirect(String path) {
    return "http://localhost:" + wireMockTarget.port() + path;
  }

  @Test
  void shouldProxyRequestWithValidCredentials_viaEnvVars() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(
                        get("/protected").willReturn(ok().withBody("Hello, world!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/protected")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Hello, world!");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }

  private static Stream<Arguments> provideValidCredentials() {
    return Stream.of(
        Arguments.of("my-user", "demo", "/protected"),
        Arguments.of("user-with?special%char", "pass%?word", "/protected"),
        Arguments.of("", "", "/path"));
  }

  @ParameterizedTest
  @MethodSource("provideValidCredentials")
  void shouldProxyRequestWithValidCredentials_parameterized(
      String user, String password, String path) throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  user,
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  password)
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get(path).willReturn(ok().withBody("Success!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl(path)))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Success!");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid"})
  void shouldFailWithInvalidCredentials(String credentials) throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  credentials,
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  credentials)
              .execute(
                  () -> {
                    wireMockTarget.stubFor(
                        get("/protected").willReturn(ok().withBody("Hello, world!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/protected")))
                            .GET()
                            .build();

                    var exception =
                        assertThrows(
                            IOException.class,
                            () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
                    // JDK HttpClient wraps 407 errors in various exception messages
                    assertThat(exception.getMessage())
                        .satisfiesAnyOf(
                            msg -> assertThat(msg).containsIgnoringCase("407"),
                            msg -> assertThat(msg).containsIgnoringCase("authentication"),
                            msg -> assertThat(msg).containsIgnoringCase("credentials"));
                  });
        });
  }

  @Test
  void shouldBypassProxyForNonProxyHosts_viaSystemProperty() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo")
              .execute(
                  () -> {
                    System.setProperty("http.nonProxyHosts", "localhost");
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Direct!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    // Use localhost URL (WireMock running locally) to test direct connection
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + wireMockTarget.port() + "/path"))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Direct!");
                    // No Via header means direct connection (not through proxy)
                    assertThat(response.headers().firstValue("Via")).isEmpty();
                  });
        });
  }

  @Test
  void shouldBypassProxyForNonProxyHosts_viaEnvVar() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo",
                  "CONNECTOR_HTTP_NON_PROXY_HOSTS",
                  "localhost")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Direct!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    // Use localhost URL - bypasses proxy because localhost is in nonProxyHosts
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrlDirect("/path")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Direct!");
                    assertThat(response.headers().firstValue("Via")).isEmpty();
                  });
        });
  }

  @Test
  void shouldBypassProxyForWildcardPattern() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo",
                  "CONNECTOR_HTTP_NON_PROXY_HOSTS",
                  "*host")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Direct!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    // Use localhost URL which matches *host pattern - bypasses proxy
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrlDirect("/path")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Direct!");
                    assertThat(response.headers().firstValue("Via")).isEmpty();
                  });
        });
  }

  @Test
  void shouldUseProxyWhenHostNotInNonProxyList() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo",
                  "CONNECTOR_HTTP_NON_PROXY_HOSTS",
                  "other.example.com|*.internal.net")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Proxied!")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/path")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Proxied!");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }

  @Test
  void shouldHandlePostRequestThroughProxy() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(
                        post("/data").willReturn(created().withBody("{\"id\": 123}")));

                    var client =
                        JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/data")))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"test\"}"))
                            .header("Content-Type", "application/json")
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(201);
                    assertThat(response.body()).isEqualTo("{\"id\": 123}");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }

  @Test
  void shouldWorkWithInstanceAPI() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Instance API!")));

                    var configurator = new JdkHttpClientProxyConfigurator(new ProxyConfiguration());
                    var client = configurator.newHttpClient();
                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/path")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Instance API!");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }

  @Test
  void shouldWorkWithManualConfigure() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  squidProxyContainer.getHost(),
                  "CONNECTOR_HTTP_PROXY_PORT",
                  squidProxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  "my-user",
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  "demo")
              .execute(
                  () -> {
                    wireMockTarget.stubFor(get("/path").willReturn(ok().withBody("Manual API!")));

                    var configurator = new JdkHttpClientProxyConfigurator(new ProxyConfiguration());
                    var builder = HttpClient.newBuilder();
                    configurator.configure(builder);
                    var client = builder.build();

                    var request =
                        HttpRequest.newBuilder()
                            .uri(URI.create(getWireMockUrl("/path")))
                            .GET()
                            .build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("Manual API!");
                    assertThat(response.headers().firstValue("Via"))
                        .isPresent()
                        .get()
                        .asString()
                        .containsIgnoringCase("squid");
                  });
        });
  }
}
