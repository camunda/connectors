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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.ConnectorRuntimeApplication;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

class SelfManagedApiSecurityAutoConfigurationTest {

  private static final String AUDIENCE = "connectors";

  private static final String BODY =
      "{\"credentialId\":\"unknown\",\"credentialRef\":\"=x\",\"tenantId\":\"t\"}";

  private static RequestBuilder validateRequest(String bearerToken) {
    return post("/configurations/validate")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(BODY);
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class)
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithoutIssuerConfigured {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Test
    void configurationsEndpoint_isDeniedEvenWithoutAnyCredentials() throws Exception {
      mvc.perform(post("/configurations/validate")).andExpect(status().isNotFound());
    }

    @Test
    void configurationsEndpoint_isDeniedEvenWithABearerToken() throws Exception {
      mvc.perform(validateRequest("any-token-at-all")).andExpect(status().isNotFound());
    }

    @Test
    void configurationsEndpoint_isDeniedEvenWhenAlreadyAuthenticated() throws Exception {
      mvc.perform(post("/configurations/validate").with(jwt())).andExpect(status().isNotFound());
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {"camunda.connector.auth.self-managed.audience=" + AUDIENCE})
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithIssuerConfigured {

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

    @Test
    void configurationsEndpoint_noAuth_isDenied() throws Exception {
      mvc.perform(post("/configurations/validate")).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withTokenFromConfiguredIssuer_isNotDenied() throws Exception {
      mvc.perform(validateRequest(acceptableToken().sign())).andExpect(status().isOk());
    }

    @Test
    void configurationsEndpoint_withMalformedToken_isDenied() throws Exception {
      mvc.perform(validateRequest("not-a-jwt")).andExpect(status().isUnauthorized());
    }

    /** Signed by a different IdP's key: the JWKS of the configured issuer cannot verify it. */
    @Test
    void configurationsEndpoint_withTokenSignedByAnotherKey_isDenied() throws Exception {
      var forgedToken =
          FOREIGN_OIDC_SERVER.token().issuer(OIDC_SERVER.issuer()).audience(AUDIENCE).sign();

      mvc.perform(validateRequest(forgedToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withTokenFromAnotherIssuer_isDenied() throws Exception {
      var otherIssuerToken = acceptableToken().issuer("https://not-the-configured-issuer").sign();

      mvc.perform(validateRequest(otherIssuerToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withExpiredToken_isDenied() throws Exception {
      var expiredToken =
          acceptableToken()
              .issuedAt(Instant.now().minus(Duration.ofHours(2)))
              .expiresAt(Instant.now().minus(Duration.ofHours(1)))
              .sign();

      mvc.perform(validateRequest(expiredToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withAnotherAudience_isDenied() throws Exception {
      mvc.perform(validateRequest(OIDC_SERVER.token().audience("something-else").sign()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withoutAnyAudience_isDenied() throws Exception {
      mvc.perform(validateRequest(OIDC_SERVER.token().sign())).andExpect(status().isUnauthorized());
    }

    private static MockOidcServer.TokenBuilder acceptableToken() {
      return OIDC_SERVER.token().audience(AUDIENCE);
    }
  }

  @Nested
  class WithIssuerButNoAudience {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @Test
    void failsToStart() {
      new WebApplicationContextRunner()
          .withUserConfiguration(SelfManagedApiSecurityAutoConfiguration.class)
          .withPropertyValues("camunda.connector.auth.self-managed.issuer=" + OIDC_SERVER.issuer())
          .run(
              context ->
                  assertThat(context)
                      .hasFailed()
                      .getFailure()
                      .rootCause()
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("camunda.connector.auth.self-managed.audience"));
    }
  }

  @Nested
  class HybridRegression {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    @AfterAll
    static void stopOidcServer() {
      OIDC_SERVER.close();
    }

    @Test
    void chainRegistersRegardlessOfClientMode() {
      new WebApplicationContextRunner()
          .withUserConfiguration(SelfManagedApiSecurityAutoConfiguration.class)
          .withPropertyValues(
              "camunda.client.mode=saas",
              "camunda.connector.auth.self-managed.issuer=" + OIDC_SERVER.issuer(),
              "camunda.connector.auth.self-managed.audience=" + AUDIENCE)
          .run(
              context ->
                  assertThat(context).hasBean("selfManagedConfigurationValidationFilterChain"));
    }
  }

  /** A permissive chain at ordinary precedence, as a consuming application might declare. */
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
      classes = {ConnectorRuntimeApplication.class, PermissiveCatchAllChain.class})
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithPermissiveCatchAllChain {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    public CamundaClient camundaClient;

    @Autowired private MockMvc mvc;

    @Test
    void routeStillDeniesBecauseTheFailClosedChainOutranksIt() throws Exception {
      mvc.perform(post("/configurations/validate")).andExpect(status().isNotFound());
    }
  }
}
