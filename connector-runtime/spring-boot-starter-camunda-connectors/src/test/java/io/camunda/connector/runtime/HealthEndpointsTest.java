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
package io.camunda.connector.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      // Define health groups manually — avoids conflict with built-in probes.enabled behaviour
      "management.endpoint.health.group.startup.include=startupCheck",
      "management.endpoint.health.group.liveness.include=zeebeClient",
      "management.endpoint.health.group.readiness.include=zeebeClient",
      "management.endpoints.web.exposure.include=health",
      "management.endpoint.health.show-details=always"
    })
@AutoConfigureMockMvc
class HealthEndpointsTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  CamundaClient camundaClient;

  @Test
  void actuatorHealth_isAccessible() throws Exception {
    var body =
        mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getContentAsString();
    assertThat(body).contains("\"status\"");
  }

  @Test
  void healthStartup_isUpAfterApplicationReady() throws Exception {
    // ApplicationReadyEvent fires when the Spring context is fully started,
    // so StartupHealthIndicator should always be UP in a @SpringBootTest
    var body =
        mockMvc
            .perform(get("/actuator/health/startup"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("\"UP\"");
  }

  @Test
  void healthLiveness_isAccessible() throws Exception {
    var result = mockMvc.perform(get("/actuator/health/liveness")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 503);
    assertThat(result.getResponse().getContentAsString()).contains("\"status\"");
  }

  @Test
  void healthReadiness_isAccessible() throws Exception {
    var result = mockMvc.perform(get("/actuator/health/readiness")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 503);
    assertThat(result.getResponse().getContentAsString()).contains("\"status\"");
  }
}
