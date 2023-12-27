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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.CamundaOperateClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = {SaaSConnectorRuntimeApplication.class, MockSaaSConfiguration.class},
    properties = {
      "camunda.saas.secrets.projectId=42",
      "zeebe.client.cloud.cluster-id=42",
      "zeebe.client.security.plaintext=true",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.auth.issuer=https://weblogin.cloud.dev.ultrawombat.com/",
      "camunda.operate.client.url=" + MockSaaSConfiguration.OPERATE_CLIENT_URL,
      "camunda.operate.client.authUrl=" + MockSaaSConfiguration.OPERATE_CLIENT_AUTH_URL,
      "camunda.operate.client.baseUrl=" + MockSaaSConfiguration.OPERATE_CLIENT_BASEURL
    })
@DirtiesContext
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class SecurityConfigurationTest {

  @Autowired private MockMvc mvc;

  // needed to access /actuator endpoints
  @Autowired RestTemplateBuilder restTemplateBuilder;
  @LocalManagementPort int managementPort;

  @MockBean
  @SuppressWarnings("unused")
  private CamundaOperateClient operateClient;

  @Test
  public void inboundEndpoint_noAuth_returns401() throws Exception {
    mvc.perform(get("/inbound")).andExpect(status().isUnauthorized());
  }

  @Test
  public void inboundEndpoint_invalidJwt_returns401() throws Exception {
    mvc.perform(get("/inbound").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void nonExistingWebhookEndpoint_returns404() throws Exception {
    // This test is to ensure that the security configuration is not applied to webhook endpoints
    // e.g. JWT is not checked even if present

    mvc.perform(get("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isNotFound());

    mvc.perform(post("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isNotFound());

    mvc.perform(put("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isNotFound());

    mvc.perform(delete("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isNotFound());
  }

  @Test
  public void webhookEndpoint_otherMethods_notAllowed() throws Exception {
    mvc.perform(options("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isUnauthorized());

    mvc.perform(patch("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isUnauthorized());

    mvc.perform(head("/inbound/non-existing").header("Authorization", "Bearer TOKEN"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void actuatorEndpoint_isAccessible() {
    ResponseEntity<String> response =
        restTemplateBuilder
            .rootUri("http://localhost:" + managementPort + "/actuator")
            .build()
            .exchange("/metrics", HttpMethod.GET, new HttpEntity<>(null), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
