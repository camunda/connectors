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
package io.camunda.connector.runtime.saas;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = {
      SaaSConnectorRuntimeApplication.class,
    },
    properties = {
      "camunda.saas.secrets.projectId=42",
      "camunda.client.enabled=true",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.cloud.organizationId=orgId",
      "camunda.connector.auth.console.audience=cloud.dev.ultrawombat.com",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "management.endpoints.web.exposure.include=*",
      "camunda.client.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.client.auth.token-url=http://localhost:0/not-used",
      "camunda.client.auth.client-id=test",
      "camunda.client.auth.client-secret=test",
      "spring.cloud.gcp.parametermanager.enabled=false"
    })
@DirtiesContext
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class InboundInstancesSecurityConfigurationTest {

  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

  @DynamicPropertySource
  static void registerOidcProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.connector.auth.issuer", OIDC_SERVER::issuer);
  }

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  @MockitoBean(answers = Answers.RETURNS_MOCKS)
  public SaaSSecretConfiguration saaSSecretConfiguration;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  public CamundaClient camundaClient;

  @Autowired private MockMvc mvc;

  @Test
  public void inboundInstancesEndpoint_noAuth_returns401() throws Exception {
    mvc.perform(get("/inbound-instances")).andExpect(status().isUnauthorized());
  }

  @Test
  public void inboundInstancesEndpoint_withAuth_returns200() throws Exception {
    mvc.perform(get("/inbound-instances").with(jwt())).andExpect(status().isOk());
  }
}
