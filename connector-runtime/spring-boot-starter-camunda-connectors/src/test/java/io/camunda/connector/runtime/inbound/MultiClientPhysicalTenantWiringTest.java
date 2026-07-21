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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that, when multiple {@code camunda.clients.*} are configured, the connector runtime
 * wires one entry per configured physical tenant across all the per-physical-tenant beans — without
 * needing a live Zeebe broker, since {@code CamundaClient} construction (and therefore this whole
 * bean graph) is lazy and does not connect eagerly.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      // marks engine-a as @Primary so the (pre-existing, out-of-scope-for-#6962)
      // single-CamundaClient-autowiring beans elsewhere (e.g. ConnectorsAutoConfiguration's FEEL
      // evaluator) can still resolve unambiguously with two clients configured.
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class MultiClientPhysicalTenantWiringTest {

  @Autowired private CamundaClientRegistry camundaClientRegistry;

  @Autowired private Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId;

  @Autowired private Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId;

  @Autowired private Map<String, ProcessInstanceClient> processInstanceClientsByPhysicalTenantId;

  @Autowired private InboundConnectorContextFactory inboundConnectorContextFactory;

  @Test
  void registryContainsBothConfiguredClients() {
    assertThat(camundaClientRegistry.clientNames())
        .containsExactlyInAnyOrder("engine-a", "engine-b");
  }

  @Test
  void everyPerPhysicalTenantMapHasOneEntryPerConfiguredPhysicalTenant() {
    assertThat(searchQueryClientsByPhysicalTenantId).containsOnlyKeys("tenanta", "tenantb");
    assertThat(correlationHandlersByPhysicalTenantId).containsOnlyKeys("tenanta", "tenantb");
    assertThat(processInstanceClientsByPhysicalTenantId).containsOnlyKeys("tenanta", "tenantb");
  }

  @Test
  void routingContextFactoryIsWiredAsASingleBean() {
    assertThat(inboundConnectorContextFactory)
        .isInstanceOf(PhysicalTenantIdRoutingInboundConnectorContextFactory.class);
  }
}
