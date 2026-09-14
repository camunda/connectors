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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The route presents a resolved credential to a caller-named endpoint and the runtime ships no
 * authentication, so it must not exist unless a deployment asked for it.
 *
 * <p>The disabled cases assert bean absence, not a status code: Spring derives the mapping from the
 * controller bean, and an unmapped path does not answer 404 here — {@code GlobalExceptionHandler}
 * advises on {@code Exception}, so it reports 500, exactly as a served-but-failing route would.
 */
class ConfigurationValidationOptInTest {

  private static final String PATH = "/configurations/validate";

  /**
   * An unregistered {@code credentialId}, so validation short-circuits to {@code UNSUPPORTED}
   * before evaluating and nothing reaches the {@code CamundaClient}, which has no broker here.
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
  class NotEnabled {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void nothingIsWired() {
      assertThat(applicationContext.getBeanNamesForType(ConfigurationValidationService.class))
          .isEmpty();
      assertThat(
              applicationContext.getBeanNamesForType(ConfigurationValidationRestController.class))
          .isEmpty();
    }
  }

  /**
   * Puts the controller's own package in a component scan, as {@code
   * SaaSConnectorRuntimeApplication} does for all of {@code io.camunda.connector}, so the
   * controller is discoverable without the importing configuration.
   */
  @TestConfiguration
  @ComponentScan(basePackages = "io.camunda.connector.runtime.configuration")
  static class BroadComponentScan {}

  /**
   * The condition on the importing configuration cannot gate a controller the scan finds directly,
   * so this is what protects the condition on the controller itself; {@link NotEnabled} passes
   * either way.
   */
  @Nested
  @SpringBootTest(
      classes = {TestConnectorRuntimeApplication.class, BroadComponentScan.class},
      properties = {
        "camunda.connector.polling.enabled=false",
        "camunda.connector.webhook.enabled=false"
      })
  class NotEnabledUnderComponentScan {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void theScannedControllerIsNotRegistered() {
      // Context startup is itself an assertion: a registered controller would have no
      // ConfigurationValidationService to inject and would fail the context instead.
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
          .perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
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
