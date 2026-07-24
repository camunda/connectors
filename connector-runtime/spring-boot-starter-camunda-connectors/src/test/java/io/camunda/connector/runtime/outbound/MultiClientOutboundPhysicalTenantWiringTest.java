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
package io.camunda.connector.runtime.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Verifies that, when multiple {@code camunda.clients.*} are configured, the outbound connector
 * runtime wires one entry per configured physical tenant across all the per-physical-tenant beans —
 * without needing a live Zeebe broker, since {@code CamundaClient} construction (and therefore this
 * whole bean graph, including job worker registration) is lazy and does not connect eagerly.
 *
 * <p>The per-physical-tenant maps are fetched by explicit bean name via {@link ApplicationContext},
 * not {@code @Autowired} field injection: this configuration also keeps legacy scalar {@code
 * documentFactory}/{@code documentStore}/{@code secretKeyCache}/{@code secretFilterFactory} beans
 * for backward compatibility, and Spring's dependency resolution special-cases any {@code
 * Map<String, X>} autowiring point by collecting all beans of type {@code X} by name — which would
 * silently resolve to the single legacy scalar bean instead of the real per-physical-tenant map.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      // marks engine-a as @Primary so the (pre-existing, out-of-scope) single-CamundaClient
      // autowiring beans elsewhere (e.g. ConnectorsAutoConfiguration's FEEL evaluator, the scalar
      // documentStore/secretKeyCache beans kept for backward compat) can still resolve
      // unambiguously with two clients configured.
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class MultiClientOutboundPhysicalTenantWiringTest {

  @Autowired private CamundaClientRegistry camundaClientRegistry;

  @Autowired private ApplicationContext applicationContext;

  @Autowired private OutboundConnectorManager outboundConnectorManager;

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapBean(String beanName) {
    return (Map<String, Object>) applicationContext.getBean(beanName, Map.class);
  }

  @Test
  void registryContainsBothConfiguredClients() {
    assertThat(camundaClientRegistry.clientNames())
        .containsExactlyInAnyOrder("engine-a", "engine-b");
  }

  @Test
  void everyPerPhysicalTenantMapHasOneEntryPerConfiguredPhysicalTenant() {
    assertThat(mapBean("documentStoresByPhysicalTenantId")).containsOnlyKeys("tenanta", "tenantb");
    assertThat(mapBean("documentFactoriesByPhysicalTenantId"))
        .containsOnlyKeys("tenanta", "tenantb");
    assertThat(mapBean("secretKeyCachesByPhysicalTenantId")).containsOnlyKeys("tenanta", "tenantb");
    assertThat(mapBean("secretFilterFactoriesByPhysicalTenantId"))
        .containsOnlyKeys("tenanta", "tenantb");
  }

  @Test
  void outboundConnectorManagerBeanWiresSuccessfully() {
    assertThat(outboundConnectorManager).isNotNull();
  }
}
