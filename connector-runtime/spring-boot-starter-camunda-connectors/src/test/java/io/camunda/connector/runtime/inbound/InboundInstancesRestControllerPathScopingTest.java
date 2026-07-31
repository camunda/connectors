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
package io.camunda.connector.runtime.inbound;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that {@code GET /inbound-instances/.../executables/{id}} only rewrites {@code
 * inbound.context} to the physical-tenant/tenant-scoped path when the caller explicitly opts in via
 * the {@code appendPhysicalTenantAndTenantToPath} query parameter — the server-wide setting (here
 * forced on) is necessary but not sufficient, per the design in {@code ConnectorDataMapper}.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {"camunda.connector.webhook.append-physical-tenant-and-tenant-to-path=true"})
@AutoConfigureMockMvc
class InboundInstancesRestControllerPathScopingTest {

  private static final ExecutableId RANDOM_ID = ExecutableId.fromDeduplicationId("dedup-id");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InboundExecutableRegistry executableRegistry;

  static class TestWebhookExecutable implements WebhookConnectorExecutable {
    @Override
    public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {
      return null;
    }
  }

  private void mockWebhookExecutable() {
    when(executableRegistry.query(any()))
        .thenReturn(
            List.of(
                new ActiveExecutableResponse(
                    RANDOM_ID,
                    TestWebhookExecutable.class,
                    List.of(
                        new InboundConnectorElement(
                            Map.of("inbound.context", "myPath", "inbound.type", "webhook"),
                            new StandaloneMessageCorrelationPoint(
                                "myPath", "=expression", "=myPath", null),
                            new ProcessElementWithRuntimeData(
                                "",
                                null,
                                null,
                                1,
                                1,
                                "",
                                null,
                                null,
                                "myTenant",
                                "myPhysicalTenant",
                                new ElementTemplateDetails("Test", "1", "icon"),
                                Map.of()))),
                    Health.up(),
                    Collections.emptyList(),
                    System.currentTimeMillis())));
  }

  @Test
  void withoutQueryParam_returnsUnscopedPath() throws Exception {
    mockWebhookExecutable();

    mockMvc
        .perform(get("/inbound-instances/executables/" + RANDOM_ID.getId()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("\"inbound.context\":\"myPath\"")));
  }

  @Test
  void withQueryParam_returnsPhysicalTenantAndTenantScopedPath() throws Exception {
    mockWebhookExecutable();

    mockMvc
        .perform(
            get("/inbound-instances/executables/" + RANDOM_ID.getId())
                .queryParam("appendPhysicalTenantAndTenantToPath", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    containsString("\"inbound.context\":\"myPhysicalTenant/myTenant/myPath\"")));
  }
}
