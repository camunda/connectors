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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /configurations/validate} resolves stored secrets and presents the resolved
 * credential to the endpoint the configuration names, while the runtime itself ships no
 * authentication, so the route must not exist unless a deployment asked for it.
 *
 * <p>The route, not the bean, is what carries the exposure, so that is what is asserted: gating the
 * {@code @Configuration} could stop publishing {@link ConfigurationValidationService} while
 * something else still mapped the handler. The bean assertions are there to guard the premise —
 * without them, a context that failed to wire validation for some unrelated reason would produce
 * the same 404, and the disabled case would pass while proving nothing.
 */
class ConfigurationValidationOptInTest {

  /**
   * Names a {@code credentialId} that is deliberately not registered, so {@code
   * ConfigurationValidationService.validate} short-circuits to {@code UNSUPPORTED} before it
   * evaluates anything. That keeps these tests off the FEEL/cluster path entirely — no request
   * reaches the {@code CamundaClient}, which points at no broker here — while still exercising the
   * real handler mapping.
   */
  private static final String BODY =
      """
      {"credentialId":"io.camunda:not-registered:1","credentialRef":"=ref","tenantId":"acme",\
      "physicalTenantId":"engine-a"}""";

  @Nested
  @SpringBootTest(
      classes = TestConnectorRuntimeApplication.class,
      properties = {
        "camunda.connector.polling.enabled=false",
        "camunda.connector.webhook.enabled=false"
      })
  @AutoConfigureMockMvc
  class NotEnabled {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void theRouteIsNotServed() throws Exception {
      mockMvc
          .perform(
              post("/configurations/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isNotFound());
    }

    @Test
    void nothingIsWired() {
      assertThat(applicationContext.getBeanNamesForType(ConfigurationValidationService.class))
          .isEmpty();
      assertThat(
              applicationContext.getBeanNamesForType(ConfigurationValidationRestController.class))
          .isEmpty();
    }
  }

  @Nested
  @SpringBootTest(
      classes = TestConnectorRuntimeApplication.class,
      properties = {
        "camunda.connector.configuration-validation.enabled=true",
        "camunda.connector.polling.enabled=false",
        "camunda.connector.webhook.enabled=false"
      })
  @AutoConfigureMockMvc
  class Enabled {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void theRouteIsServed() throws Exception {
      mockMvc
          .perform(
              post("/configurations/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isOk());
    }

    @Test
    void validationIsWired() {
      assertThat(applicationContext.getBeanNamesForType(ConfigurationValidationService.class))
          .isNotEmpty();
      assertThat(
              applicationContext.getBeanNamesForType(ConfigurationValidationRestController.class))
          .isNotEmpty();
    }
  }
}
