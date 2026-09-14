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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.ConnectorRuntimeApplication;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

class SelfManagedApiSecurityConfigurationTest {

  private static final String BODY =
      "{\"credentialId\":\"unknown\",\"credentialRef\":\"=x\",\"tenantId\":\"t\"}";

  /**
   * Default self-managed configuration: no {@code camunda.connector.auth.self-managed.issuer} is
   * set. The route must fail closed rather than behave like it does today (wide open) — as a 404,
   * matching how the Hub adapter already treats a too-old runtime, rather than a 401/403 that would
   * surface as a visible failure in Hub.
   */
  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {"management.server.port=0"})
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
      mvc.perform(post("/configurations/validate").with(jwt())).andExpect(status().isNotFound());
    }
  }

  /**
   * An operator opts in by pointing {@code camunda.connector.auth.self-managed.issuer} at their own
   * identity provider (the same one Hub's forwarded bearer token is issued from).
   */
  @Nested
  @SpringBootTest(
      webEnvironment = WebEnvironment.RANDOM_PORT,
      classes = ConnectorRuntimeApplication.class,
      properties = {"management.server.port=0"})
  @DirtiesContext
  @AutoConfigureMockMvc
  class WithIssuerConfigured {

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
    void configurationsEndpoint_noAuth_isDenied() throws Exception {
      mvc.perform(post("/configurations/validate")).andExpect(status().isUnauthorized());
    }

    @Test
    void configurationsEndpoint_withAuth_isNotDenied() throws Exception {
      mvc.perform(
              post("/configurations/validate")
                  .with(jwt())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isOk());
    }
  }
}
