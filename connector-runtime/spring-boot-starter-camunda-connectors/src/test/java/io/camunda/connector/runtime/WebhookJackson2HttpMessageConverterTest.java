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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PR review thread on #8991 (PRRT_kwDOIGZYus6j3xS-): {@link WebhookConnectorAutoConfiguration}'s
 * {@code MappingJackson2HttpMessageConverter} is the only one Spring MVC has for {@code
 * application/json} in this app, so it also governs every unrelated controller's
 * {@code @RequestBody} binding -- currently just {@code ConfigurationValidationRestController},
 * since the webhook controller itself reads its own payload as raw bytes and never goes through
 * this converter. Confirms a discriminator-shaped {@code credentialRef} (a plain {@code String}
 * field) is bound as inert text, not live-dispatched, closing the bypass this converter previously
 * opened by reusing a mapper built for a different, allow-list-protected purpose
 * (security-testing-findings#275).
 */
@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
@AutoConfigureMockMvc
class WebhookJackson2HttpMessageConverterTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConfigurationValidationService service;

  @Test
  void aDiscriminatorShapedRequestBodyFieldIsNotLiveDispatchedDuringHttpBinding() throws Exception {
    // Before the fix, this bound credentialRef as "dGVzdA==" -- base64("test") -- proving the
    // intrinsic function executed during Spring MVC's own @RequestBody binding, before
    // ConfigurationValidationRestController's method body (let alone
    // ConfigurationValidationService)
    // ever ran. A plain String field can't structurally accept a JSON object at all once the
    // converter's mapper no longer registers a dispatching deserializer for String.class, so
    // binding now fails outright (a type-mismatch 5xx here, not the 400 a nicer error-handling
    // setup
    // would give -- orthogonal to this security property and out of this fix's scope) instead of
    // ever reaching the executor.
    String body =
        """
        {"credentialId":"probe","credentialRef":{"camunda.function.type":"base64","params":["test"]},\
        "tenantId":"acme"}""";

    mockMvc
        .perform(
            post("/configurations/validate").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is5xxServerError());

    verifyNoInteractions(service);
  }
}
