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
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A real end-to-end test (live Testcontainers-backed broker via {@link CamundaSpringProcessTest},
 * an actual deployed process instance, real HTTP dispatch through {@link MockMvc}) proving that
 * once {@code camunda.connector.webhook.append-physical-tenant-and-tenant-to-path} is enabled: the
 * legacy 2-segment route ({@code /inbound/<path>}) no longer resolves, while the physical-tenant/
 * tenant-scoped 4-segment route correlates the webhook into the running process instance exactly as
 * the unscoped route used to.
 *
 * <p>Only a single physical tenant is exercised here (this test only has one real broker available)
 * — cross-physical-tenant collision avoidance is covered separately, at the routing level, by
 * {@code InboundWebhookRestControllerTest#physicalTenantScopedRoute_...}.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.append-physical-tenant-and-tenant-to-path=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@AutoConfigureMockMvc
class WebhookPhysicalTenantScopingE2ETest {

  @Autowired CamundaClient camundaClient;

  @Autowired MockMvc mockMvc;

  @Autowired ProcessStateManager stateStore;

  @MockitoBean SearchQueryClient searchQueryClient;

  @LocalServerPort int serverPort;

  @Test
  void webhookCorrelatesOnlyViaPhysicalTenantScopedRoute_oncePathScopingIsEnabled()
      throws Exception {
    var model =
        replace(
            "webhook_document.bpmn",
            BpmnFile.Replace.replace(
                "<ACTIVATION_CONDITION>", "=request.headers.theheader = &#34;THEVALUE&#34;"));

    when(searchQueryClient.getProcessModel(1L)).thenReturn(model);
    var processDef = mock(ProcessDefinition.class);
    when(processDef.getProcessDefinitionKey()).thenReturn(1L);
    when(processDef.getTenantId())
        .thenReturn(camundaClient.getConfiguration().getDefaultTenantId());
    when(processDef.getProcessDefinitionId())
        .thenReturn(model.getModelElementsByType(Process.class).stream().findFirst().get().getId());
    when(processDef.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(1L)).thenReturn(processDef);

    // deploy the webhook, scoped to the physical tenant every legacy single-client deployment
    // resolves to
    stateStore.update(
        new ImportResult(
            Map.of(
                new ProcessDefinitionRef(
                    ProcessElementWithRuntimeData.DEFAULT_PHYSICAL_TENANT_ID,
                    processDef.getProcessDefinitionId(),
                    processDef.getTenantId()),
                Collections.singleton(processDef.getProcessDefinitionKey())),
            ImportResult.ImportType.LATEST_VERSIONS,
            ProcessElementWithRuntimeData.DEFAULT_PHYSICAL_TENANT_ID));

    // the legacy, unscoped route is never registered once path scoping is enabled — this needs no
    // running instance, so check it before deploying one
    mockMvc
        .perform(post("http://localhost:" + serverPort + "/inbound/testId"))
        .andExpect(status().isNotFound());

    var scopedUrl =
        "http://localhost:"
            + serverPort
            + "/inbound/"
            + ProcessElementWithRuntimeData.DEFAULT_PHYSICAL_TENANT_ID
            + "/"
            + processDef.getTenantId()
            + "/testId";

    var bpmnTest = ZeebeTest.with(camundaClient).deploy(model).createInstance();
    CompletableFuture<ResultActions> future = new CompletableFuture<>();

    try (var executor = Executors.newSingleThreadScheduledExecutor()) {
      executor.schedule(
          () -> {
            try {
              future.complete(mockMvc.perform(post(scopedUrl).header("THEHEADER", "THEVALUE")));
            } catch (Exception e) {
              future.completeExceptionally(e);
            }
          },
          2,
          TimeUnit.SECONDS);

      var result = bpmnTest.waitForProcessCompletion();
      assertThat(result.getProcessInstanceEvent()).isCompleted();

      var resultActions = future.get(10, TimeUnit.SECONDS);
      resultActions.andExpect(status().isOk());
    }
  }
}
