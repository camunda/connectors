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
package io.camunda.connector.runtime.inbound.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.event.CamundaClientClosingSpringEvent;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

/**
 * The multi-client counterpart of {@code ImportSchedulersLifecycleWiringTest}, which can only cover
 * the degenerate single-client case where a client's resolved physical tenant ID and its configured
 * name are the same string.
 *
 * <p>This is the shape production runs in, and the one that pins the actual contract: the entries
 * are keyed by physical tenant ID ({@code tenanta}/{@code tenantb}), the lifecycle events carry
 * client names ({@code engine-a}/{@code engine-b}), and each event must therefore resolve its
 * client's physical tenant ID to land on the key the startup snapshot already used — never add a
 * name-keyed second entry. It also pins that a lifecycle event is scoped to one physical tenant:
 * stopping one client must leave the other one polling.
 *
 * <p>Needs no live broker, for the same reason {@code MultiClientPhysicalTenantWiringTest} does
 * not: {@code CamundaClient} construction is lazy and does not connect eagerly. Polling is pushed
 * past the end of the test via a long initial delay, since only the registration is of interest.
 */
@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      ImportSchedulersMultiClientLifecycleWiringTest.CreatedClientRecorder.class
    },
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      // marks engine-a as @Primary so the single-CamundaClient-autowiring beans elsewhere (e.g.
      // ConnectorsAutoConfiguration's FEEL evaluator) can still resolve unambiguously
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=true",
      "camunda.connector.polling.initial-delay=600000",
      "camunda.connector.webhook.enabled=false"
    })
class ImportSchedulersMultiClientLifecycleWiringTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private ImportSchedulers importSchedulers;

  @Autowired private CamundaClientRegistry camundaClientRegistry;

  @Autowired private CreatedClientRecorder createdClientRecorder;

  /**
   * Records what the real producer actually published at context start, so the assertion below
   * observes the event rather than merely repeating the lookup it is supposed to be compared
   * against.
   */
  @TestConfiguration
  static class CreatedClientRecorder {

    private final Map<String, CamundaClient> publishedClientsByName = new ConcurrentHashMap<>();

    @EventListener
    void record(CamundaClientCreatedSpringEvent event) {
      publishedClientsByName.put(event.getClientName(), event.getClient());
    }
  }

  /**
   * Pins why the other startup-snapshotted consumers of a per-physical-tenant {@code
   * SearchQueryClient} map — {@code ProcessDefinitionInspector}, which {@code
   * ProcessStateManagerImpl} fetches BPMN models through — do not need refreshing alongside the
   * polling entries here: on the production path the client a lifecycle event carries is the very
   * same instance the startup snapshot resolved, because {@code MultiCamundaLifecycleEventProducer}
   * publishes {@code registry.get(name)} and {@code PhysicalTenantIds.resolveClient} snapshots
   * {@code registry.get(name)}.
   *
   * <p>Asserted against the client the producer really published — comparing two {@code
   * registry.get(name)} calls would only have re-proven that the client beans are singletons, and
   * would stay green if the producer ever began publishing a different instance. That is the case
   * this test exists to catch, because it is the point at which polling would start using a
   * replacement client while models were still resolved through the snapshotted one.
   */
  @Test
  void theProducerPublishesTheSameClientInstanceTheStartupSnapshotsHold() {
    assertThat(createdClientRecorder.publishedClientsByName)
        .containsOnlyKeys("engine-a", "engine-b");
    assertThat(createdClientRecorder.publishedClientsByName.get("engine-a"))
        .isSameAs(camundaClientRegistry.get("engine-a"));
    assertThat(createdClientRecorder.publishedClientsByName.get("engine-b"))
        .isSameAs(camundaClientRegistry.get("engine-b"));
  }

  /**
   * One sequential test rather than several: the events mutate context-wide state, so independent
   * test methods sharing this context would be order-dependent.
   */
  @Test
  void clientLifecycleEventsAreScopedToOnePhysicalTenantEach() {
    assertThat(importSchedulers.activePhysicalTenantIds())
        .containsExactlyInAnyOrder("tenanta", "tenantb");

    var engineA = camundaClientRegistry.get("engine-a");

    // stopping one client must leave the other physical tenant polling
    applicationContext.publishEvent(new CamundaClientClosingSpringEvent(this, engineA, "engine-a"));
    assertThat(importSchedulers.activePhysicalTenantIds()).containsExactly("tenantb");

    // and starting it again must restore it under "tenanta" — no "engine-a" key appears alongside
    // it, so the startup keys every other per-physical-tenant map in the runtime shares stay intact
    applicationContext.publishEvent(new CamundaClientCreatedSpringEvent(this, engineA, "engine-a"));
    assertThat(importSchedulers.activePhysicalTenantIds())
        .containsExactlyInAnyOrder("tenanta", "tenantb");
  }
}
