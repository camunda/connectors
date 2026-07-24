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
package io.camunda.connector.e2e;

import static io.camunda.connector.e2e.BpmnFile.replace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies, through the real Spring wiring (BPMN parsing via {@code ProcessDefinitionInspector},
 * state import via {@link ProcessStateManager}, registration via {@link WebhookConnectorRegistry}),
 * that two physical tenants deploying the exact same raw webhook path register as two distinct,
 * independently addressable connectors rather than colliding into one — the core guarantee added by
 * {@code camunda.connector.webhook.append-physical-tenant-and-tenant-to-path}.
 *
 * <p>Deliberately Docker-free: no {@code @CamundaSpringProcessTest}/{@code ZeebeTest}, since
 * nothing here needs a live broker. The assertions stop at registration/routing rather than
 * triggering the webhook end-to-end, since {@code correlate()} would attempt a real gRPC call
 * against the fake {@code camunda.clients.*} addresses configured below.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.webhook.append-physical-tenant-and-tenant-to-path=true"
    })
@AutoConfigureMockMvc
class WebhookPhysicalTenantScopingE2ETest {

  private static final String BPMN_PROCESS_ID = "Process_1tpx1wl";
  private static final String RAW_PATH = "testId";
  private static final String TENANT_ID = "<default>";

  @MockitoBean SearchQueryClient searchQueryClient;

  @Autowired ProcessStateManager stateStore;

  @Autowired WebhookConnectorRegistry webhookConnectorRegistry;

  @Autowired MockMvc mockMvc;

  @Test
  void sameRawPath_onTwoPhysicalTenants_registersAsDistinctConnectors_noCollision()
      throws Exception {
    var model =
        replace(
            "webhook_document.bpmn", BpmnFile.Replace.replace("<ACTIVATION_CONDITION>", "=true"));
    when(searchQueryClient.getProcessModel(anyLong())).thenReturn(model);

    var processDefinitionA = mock(ProcessDefinition.class);
    when(processDefinitionA.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(1L)).thenReturn(processDefinitionA);

    var processDefinitionB = mock(ProcessDefinition.class);
    when(processDefinitionB.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(2L)).thenReturn(processDefinitionB);

    // deploy the same raw webhook path ("testId") on two different physical tenants
    stateStore.update(
        new ImportResult(
            Map.of(new ProcessDefinitionRef("tenanta", BPMN_PROCESS_ID, TENANT_ID), Set.of(1L)),
            ImportType.LATEST_VERSIONS,
            "tenanta"));
    stateStore.update(
        new ImportResult(
            Map.of(new ProcessDefinitionRef("tenantb", BPMN_PROCESS_ID, TENANT_ID), Set.of(2L)),
            ImportType.LATEST_VERSIONS,
            "tenantb"));

    // registration is processed asynchronously off an internal event queue, so poll rather than
    // assert immediately
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(webhookConnectorRegistry.getActiveWebhook("tenanta", TENANT_ID, RAW_PATH))
                  .isPresent();
              assertThat(webhookConnectorRegistry.getActiveWebhook("tenantb", TENANT_ID, RAW_PATH))
                  .isPresent();
            });

    var connectorA = webhookConnectorRegistry.getActiveWebhook("tenanta", TENANT_ID, RAW_PATH);
    var connectorB = webhookConnectorRegistry.getActiveWebhook("tenantb", TENANT_ID, RAW_PATH);

    // both physical tenants registered under the same raw path without one overwriting the other
    assertThat(connectorA.get().id()).isNotEqualTo(connectorB.get().id());

    // the legacy, unscoped 2-segment route was never populated once path scoping is enabled
    assertThat(webhookConnectorRegistry.getActiveWebhook(RAW_PATH)).isEmpty();
    mockMvc.perform(post("/inbound/" + RAW_PATH)).andExpect(status().isNotFound());
  }
}
