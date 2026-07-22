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
package io.camunda.connector.runtime.outbound.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationRequest;
import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigurationValidationRestControllerTest {

  private static final String BODY =
      """
      {"credentialId":"io.camunda:aws-credential:1","credentialRef":"=ref","tenantId":"acme"}""";

  private ConfigurationValidationService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = Mockito.mock(ConfigurationValidationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ConfigurationValidationRestController(service)).build();
  }

  @Test
  void success() throws Exception {
    when(service.validate(any(ConfigurationValidationRequest.class)))
        .thenReturn(ConfigurationValidationResult.success());

    mockMvc
        .perform(
            post("/outbound/configurations/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        // code/message are null and must be omitted from the response.
        .andExpect(content().string("{\"status\":\"SUCCESS\"}"));
  }

  @Test
  void failureIncludesCodeAndMessage() throws Exception {
    when(service.validate(any(ConfigurationValidationRequest.class)))
        .thenReturn(ConfigurationValidationResult.failure("UNAUTHORIZED", "nope"));

    mockMvc
        .perform(
            post("/outbound/configurations/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string("{\"status\":\"FAILURE\",\"code\":\"UNAUTHORIZED\",\"message\":\"nope\"}"));
  }

  @Test
  void unsupported() throws Exception {
    when(service.validate(any(ConfigurationValidationRequest.class)))
        .thenReturn(ConfigurationValidationResult.unsupported());

    mockMvc
        .perform(
            post("/outbound/configurations/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(content().string("{\"status\":\"UNSUPPORTED\"}"));
  }
}
