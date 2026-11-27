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
package io.camunda.connector.runtime.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /configurations/validate} resolves stored secrets, so the starter alone must not
 * expose it: only a runtime that protects the route imports {@link
 * ConfigurationValidationConfiguration}.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
@AutoConfigureMockMvc
class ConfigurationValidationNotAutoConfiguredTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void starterDoesNotRegisterTheValidationRoute() throws Exception {
    assertThat(applicationContext.getBeanNamesForType(ConfigurationValidationRestController.class))
        .isEmpty();
    // The runtime answers any unmapped path the same way, so the route must be indistinguishable
    // from a path that does not exist.
    var body =
        "{\"credentialId\":\"io.camunda.connectors:rest-authentication:1\",\"credentialRef\":\"={}\"}";
    var validate =
        mockMvc
            .perform(
                post("/configurations/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();
    var unmapped =
        mockMvc
            .perform(
                post("/configurations/does-not-exist")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();
    assertThat(validate.getStatus()).isEqualTo(unmapped.getStatus());
    assertThat(validate.getContentAsString()).doesNotContain("status");
  }
}
