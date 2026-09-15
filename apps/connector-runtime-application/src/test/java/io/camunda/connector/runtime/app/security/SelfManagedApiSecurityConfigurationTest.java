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
package io.camunda.connector.runtime.app.security;

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
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

class SelfManagedApiSecurityConfigurationTest {

  private static final String BODY =
      "{\"credentialId\":\"unknown\",\"credentialRef\":\"=x\",\"tenantId\":\"t\"}";

  private static RequestBuilder validateRequest(String bearerToken) {
    return post("/configurations/validate")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(BODY);
  }

  /**
   * Default self-managed configuration: no {@code camunda.connector.auth.self-managed.issuer} is
   * set. The route must fail closed rather than behave like it does today (wide open) — as a 404,
   * matching how the Hub adapter already treats a too-old runtime, rather than a 401/403 that would
   * surface as a visible failure in Hub.
   */
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

    /** Not even an already-authenticated caller gets through: the chain is deny-all, not a gate. */
    @Test
    void configurationsEndpoint_isDeniedEvenWhenAlreadyAuthenticated() throws Exception {
      mvc.perform(post("/configurations/validate").with(jwt())).andExpect(status().isNotFound());
    }
  }

  /**
   * An operator opts in by pointing {@code camunda.connector.auth.self-managed.issuer} at their own
   * identity provider (the same one Hub's forwarded bearer token is issued from). Every case here
   * goes through the real {@code Authorization: Bearer} header so the configured {@code JwtDecoder}
   * — signature, expiry and issuer — is what decides, rather than a pre-authenticated stand-in
   * placed straight into the security context.
   */
  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class)
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithIssuerConfigured {

    private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

    /** A second issuer, used to sign tokens this runtime must not accept. */
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
      mvc.perform(validateRequest(OIDC_SERVER.token().sign())).andExpect(status().isOk());
    }

    @Test
    void configurationsEndpoint_withMalformedToken_isDenied() throws Exception {
      mvc.perform(validateRequest("not-a-jwt")).andExpect(status().isUnauthorized());
    }

    /** Signed by a different IdP's key: the JWKS of the configured issuer cannot verify it. */
    @Test
    void configurationsEndpoint_withTokenSignedByAnotherKey_isDenied() throws Exception {
      var forgedToken = FOREIGN_OIDC_SERVER.token().issuer(OIDC_SERVER.issuer()).sign();

      mvc.perform(validateRequest(forgedToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withTokenFromAnotherIssuer_isDenied() throws Exception {
      var otherIssuerToken = OIDC_SERVER.token().issuer("https://not-the-configured-issuer").sign();

      mvc.perform(validateRequest(otherIssuerToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withExpiredToken_isDenied() throws Exception {
      var expiredToken =
          OIDC_SERVER
              .token()
              .issuedAt(Instant.now().minus(Duration.ofHours(2)))
              .expiresAt(Instant.now().minus(Duration.ofHours(1)))
              .sign();

      mvc.perform(validateRequest(expiredToken)).andExpect(status().isUnauthorized());
    }
  }

  /**
   * {@code camunda.connector.auth.self-managed.audience} narrows acceptance further: a token from
   * the configured issuer is only good enough if it was also minted for this runtime.
   */
  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {"camunda.connector.auth.self-managed.audience=connectors"})
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithAudienceConfigured {

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
    void configurationsEndpoint_withRequiredAudience_isNotDenied() throws Exception {
      mvc.perform(validateRequest(OIDC_SERVER.token().audience("connectors").sign()))
          .andExpect(status().isOk());
    }

    @Test
    void configurationsEndpoint_withAnotherAudience_isDenied() throws Exception {
      mvc.perform(validateRequest(OIDC_SERVER.token().audience("something-else").sign()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withoutAudience_isDenied() throws Exception {
      mvc.perform(validateRequest(OIDC_SERVER.token().sign())).andExpect(status().isUnauthorized());
    }
  }

  /**
   * Hybrid regression, isolated from the rest of the application context (a full
   * {@code @SpringBootTest} with {@code camunda.client.mode=saas} pulls in unrelated CamundaClient
   * property validation for SaaS-style connections, which has nothing to do with this class): this
   * module never carries {@code camunda-saas-bundle}'s security classes, regardless of {@code
   * camunda.client.mode} — a self-managed runtime reaching a SaaS-hosted orchestration cluster
   * (Hybrid) legitimately sets that property to {@code saas}. Protection must still register in
   * that case, proving the exclusion is keyed off the SaaS bundle's class being absent, not off
   * this property (which {@link SelfManagedApiSecurityConfiguration} no longer reads at all).
   */
  @Nested
  class HybridRegression {

    private final WebApplicationContextRunner contextRunner =
        new WebApplicationContextRunner()
            .withUserConfiguration(SelfManagedApiSecurityConfiguration.class);

    @Test
    void staysActiveRegardlessOfClientMode() {
      contextRunner
          .withPropertyValues("camunda.client.mode=saas")
          .run(
              context ->
                  assertThat(context)
                      .hasBean("selfManagedConfigurationValidationDenyAllFilterChain"));
    }
  }
}
