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
package io.camunda.connector.runtime.bundle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.ConnectorRuntimeApplication;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class SelfManagedManagementApiSecurityTest {

  private static final String AUDIENCE = "connectors";

  private static final String SECURE_MANAGEMENT_API =
      "camunda.connector.auth.self-managed.secure-management-api";

  private static final String[] MANAGEMENT_ROUTES = {
    "/inbound",
    "/tenants/default/inbound",
    "/tenants/default/inbound/process/element/logs",
    "/inbound-instances",
    "/inbound-instances/io.camunda:webhook:1",
    "/inbound-instances/io.camunda:webhook:1/executables/some-executable",
    "/inbound-instances/io.camunda:webhook:1/executables/some-executable/health",
    "/inbound-instances/io.camunda:webhook:1/executables/some-executable/logs"
  };

  private static final String[] READABLE_ROUTES = {
    "/inbound", "/tenants/default/inbound", "/inbound-instances"
  };

  private static Stream<RequestBuilder> webhookRequests() {
    var path = "/inbound/my-webhook";
    return Stream.of(
        get(path),
        head(path),
        post(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
        put(path).contentType(MediaType.APPLICATION_JSON).content("{}"),
        delete(path));
  }

  private static int statusOf(MockMvc mvc, RequestBuilder request) throws Exception {
    MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
    return response.getStatus();
  }

  private static int rawStatus(int port, String requestLine, String... headers) throws Exception {
    var request =
        new StringBuilder(requestLine)
            .append(" HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n");
    for (String header : headers) {
      request.append(header).append("\r\n");
    }
    request.append("\r\n");
    try (var socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10_000);
      socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
      var reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
      return Integer.parseInt(reader.readLine().split(" ")[1]);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class)
  @DirtiesContext
  @AutoConfigureMockMvc
  class ByDefault {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Value("${local.server.port}")
    private int port;

    @Test
    void managementRoutes_stayOpen() throws Exception {
      for (String path : READABLE_ROUTES) {
        assertThat(statusOf(mvc, get(path))).as("GET %s", path).isEqualTo(200);
      }
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.bundle.security.SelfManagedManagementApiSecurityTest#webhookRequests")
    void webhooks_stayAnonymous(RequestBuilder request) throws Exception {
      assertThat(statusOf(mvc, request)).isNotIn(401, 403);
    }

    @Test
    void healthProbe_staysAnonymous() throws Exception {
      assertThat(statusOf(mvc, get("/actuator/health"))).isNotIn(401, 403);
    }

    @Test
    void requestHandling_matchesTheRuntimeWithoutSpringSecurity() throws Exception {
      assertThat(rawStatus(port, "GET /inbound/my-webhook;jsessionid=abc")).isEqualTo(404);
      assertThat(rawStatus(port, "GET //inbound/my-webhook")).isEqualTo(500);
      assertThat(rawStatus(port, "GET /inbound/my%2Fwebhook")).isEqualTo(400);
      assertThat(rawStatus(port, "GET /inbound/my%25webhook")).isEqualTo(404);
      assertThat(
              rawStatus(
                  port, "POST /inbound/my-webhook", "X-Custom: a\u0007b", "Content-Length: 0"))
          .isEqualTo(400);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {
        "camunda.connector.auth.self-managed.audience=" + AUDIENCE,
        SECURE_MANAGEMENT_API + "=true"
      })
  @DirtiesContext
  @AutoConfigureMockMvc
  class WhenSecured {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    private static final MockOidcServer FOREIGN_OIDC_SERVER = MockOidcServer.start();

    @DynamicPropertySource
    static void registerOidcProperties(DynamicPropertyRegistry registry) {
      registry.add("camunda.connector.auth.self-managed.issuer", OIDC_SERVER::issuer);
    }

    @AfterAll
    static void stopOidcServers() {
      OIDC_SERVER.close();
      FOREIGN_OIDC_SERVER.close();
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Value("${local.server.port}")
    private int port;

    @Test
    void managementRoutes_withoutToken_areDenied() throws Exception {
      for (String path : MANAGEMENT_ROUTES) {
        assertThat(statusOf(mvc, get(path))).as("GET %s", path).isEqualTo(401);
      }
    }

    @Test
    void inboundInstances_withoutToken_areDeniedForOtherMethods() throws Exception {
      mvc.perform(post("/inbound-instances/io.camunda:webhook:1/executables/some-executable"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void listing_isNotReachableAnonymouslyThroughOtherMethodsOrATrailingSlash() throws Exception {
      assertThat(statusOf(mvc, head("/inbound"))).isEqualTo(401);
      assertThat(statusOf(mvc, post("/inbound"))).isEqualTo(401);
      assertThat(statusOf(mvc, get("/inbound/"))).isNotEqualTo(200);
    }

    @Test
    void managementRoutes_withValidToken_areAvailable() throws Exception {
      var token = acceptableToken().sign();
      for (String path : READABLE_ROUTES) {
        assertThat(statusOf(mvc, withToken(get(path), token))).as("GET %s", path).isEqualTo(200);
      }
    }

    @Test
    void managementRoutes_withMalformedToken_areDenied() throws Exception {
      mvc.perform(withToken(get("/inbound"), "not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void managementRoutes_withTokenSignedByAnotherKey_areDenied() throws Exception {
      var forgedToken =
          FOREIGN_OIDC_SERVER.token().issuer(OIDC_SERVER.issuer()).audience(AUDIENCE).sign();

      mvc.perform(withToken(get("/inbound"), forgedToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void managementRoutes_withTokenFromAnotherIssuer_areDenied() throws Exception {
      var otherIssuerToken = acceptableToken().issuer("https://not-the-configured-issuer").sign();

      mvc.perform(withToken(get("/inbound"), otherIssuerToken))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void managementRoutes_withExpiredToken_areDenied() throws Exception {
      var expiredToken =
          acceptableToken()
              .issuedAt(Instant.now().minus(Duration.ofHours(2)))
              .expiresAt(Instant.now().minus(Duration.ofHours(1)))
              .sign();

      mvc.perform(withToken(get("/inbound"), expiredToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void managementRoutes_withAnotherAudience_areDenied() throws Exception {
      mvc.perform(withToken(get("/inbound"), OIDC_SERVER.token().audience("other").sign()))
          .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.bundle.security.SelfManagedManagementApiSecurityTest#webhookRequests")
    void webhooks_stayAnonymous(RequestBuilder request) throws Exception {
      assertThat(statusOf(mvc, request)).isNotIn(401, 403);
    }

    @Test
    void healthProbe_staysAnonymous() throws Exception {
      assertThat(statusOf(mvc, get("/actuator/health"))).isNotIn(401, 403);
    }

    @Test
    void requestFirewall_rejectsPathParametersButLetsWebhooksThrough() throws Exception {
      assertThat(rawStatus(port, "GET /inbound;x=1")).isEqualTo(400);
      assertThat(rawStatus(port, "GET /inbound/my-webhook")).isNotIn(401, 403);
    }

    private static MockOidcServer.TokenBuilder acceptableToken() {
      return OIDC_SERVER.token().audience(AUDIENCE);
    }

    private static RequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
      return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {
        "spring.mvc.servlet.path=/api",
        "camunda.connector.auth.self-managed.audience=" + AUDIENCE,
        SECURE_MANAGEMENT_API + "=true"
      })
  @DirtiesContext
  class WhenSecuredUnderAServletPath {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @DynamicPropertySource
    static void registerOidcProperties(DynamicPropertyRegistry registry) {
      registry.add("camunda.connector.auth.self-managed.issuer", OIDC_SERVER::issuer);
    }

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Value("${local.server.port}")
    private int port;

    @Test
    void managementRoutes_withoutToken_areDenied() throws Exception {
      for (String path : READABLE_ROUTES) {
        assertThat(rawStatus(port, "GET /api" + path)).as("GET /api%s", path).isEqualTo(401);
      }
    }

    @Test
    void webhook_staysAnonymous() throws Exception {
      assertThat(rawStatus(port, "GET /api/inbound/my-webhook")).isNotIn(401, 403);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {
        "server.servlet.context-path=/connector",
        "camunda.connector.auth.self-managed.audience=" + AUDIENCE,
        SECURE_MANAGEMENT_API + "=true"
      })
  @DirtiesContext
  @AutoConfigureMockMvc
  class WhenSecuredUnderAContextPath {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @DynamicPropertySource
    static void registerOidcProperties(DynamicPropertyRegistry registry) {
      registry.add("camunda.connector.auth.self-managed.issuer", OIDC_SERVER::issuer);
    }

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Test
    void listing_withoutToken_isDenied() throws Exception {
      mvc.perform(get("/connector/inbound").contextPath("/connector"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_staysAnonymous() throws Exception {
      assertThat(statusOf(mvc, get("/connector/inbound/my-webhook").contextPath("/connector")))
          .isNotIn(401, 403);
    }
  }

  @Nested
  class WhenSecuredWithoutIssuer {

    @Test
    void failsToStart() {
      new WebApplicationContextRunner()
          .withUserConfiguration(SelfManagedApiSecurityAutoConfiguration.class)
          .withPropertyValues(
              SECURE_MANAGEMENT_API + "=true",
              "camunda.connector.auth.self-managed.audience=" + AUDIENCE)
          .run(context ->
              assertThat(context)
                  .hasFailed()
                  .getFailure()
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining(SECURE_MANAGEMENT_API));
    }
  }

  @Nested
  class WhenSecuredWithoutAudience {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @Test
    void failsToStart() {
      new WebApplicationContextRunner()
          .withUserConfiguration(SelfManagedApiSecurityAutoConfiguration.class)
          .withPropertyValues(
              SECURE_MANAGEMENT_API + "=true",
              "camunda.connector.auth.self-managed.issuer=" + OIDC_SERVER.issuer())
          .run(context ->
              assertThat(context)
                  .hasFailed()
                  .getFailure()
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("camunda.connector.auth.self-managed.audience"));
    }
  }

  @TestConfiguration
  static class PermissiveCatchAllChain {

    @Bean
    @Order(0)
    SecurityFilterChain catchAll(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.ignoringRequestMatchers("/**"))
          .securityMatchers(matchers -> matchers.requestMatchers("/**"))
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .build();
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = {ConnectorRuntimeApplication.class, PermissiveCatchAllChain.class},
      properties = {
        "camunda.connector.auth.self-managed.audience=" + AUDIENCE,
        SECURE_MANAGEMENT_API + "=true"
      })
  @DirtiesContext
  @AutoConfigureMockMvc
  class WhenSecuredWithPermissiveCatchAllChain {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @DynamicPropertySource
    static void registerOidcProperties(DynamicPropertyRegistry registry) {
      registry.add("camunda.connector.auth.self-managed.issuer", OIDC_SERVER::issuer);
    }

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Test
    void listing_stillRequiresAToken() throws Exception {
      mvc.perform(get("/inbound")).andExpect(status().isUnauthorized());
    }
  }

  @TestConfiguration
  static class AuthenticatedCatchAllChain {

    @Bean
    @Order(0)
    SecurityFilterChain authenticatedCatchAll(HttpSecurity http) throws Exception {
      return http.securityMatchers(matchers -> matchers.requestMatchers("/**"))
          .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .httpBasic(Customizer.withDefaults())
          .build();
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = {ConnectorRuntimeApplication.class, AuthenticatedCatchAllChain.class})
  @DirtiesContext
  @AutoConfigureMockMvc
  class ByDefaultWithAnApplicationChain {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Test
    void managementRoutes_followTheApplicationChain() throws Exception {
      for (String path : READABLE_ROUTES) {
        assertThat(statusOf(mvc, get(path))).as("GET %s", path).isEqualTo(401);
      }
    }
  }
}
