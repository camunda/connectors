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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.jobhandling.JobHandlerFactory.JobHandlerFactoryContext;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.app.TestPrototypeOutboundConnector;
import io.camunda.connector.runtime.app.TestSingletonOutboundConnector;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end verification of the #6961 outbound multi-engine instance isolation fix, using a real
 * Spring context with two physical tenants configured (mirroring {@link
 * MultiClientOutboundPhysicalTenantWiringTest}) and a mocked {@link JobWorkerManager} to capture
 * the {@link ManagedJobWorker} registrations {@link OutboundConnectorManager} produces per physical
 * tenant — without needing a live Zeebe broker, since {@code CamundaClient} construction is lazy
 * (see the class-level Javadoc on {@link MultiClientOutboundPhysicalTenantWiringTest}).
 *
 * <p>Both a {@code @Scope("prototype")} and a (default) singleton-scoped connector fixture are
 * exercised, to confirm isolation does not depend on connector authors opting into prototype scope:
 * {@link OutboundConnectorRuntimeConfiguration} wires connector instance suppliers via {@code
 * AutowireCapableBeanFactory#createBean(Class)}, which always constructs a brand-new, unmanaged
 * instance regardless of the bean definition's declared scope.
 *
 * <p>Reflection is used over the resulting {@code SpringConnectorJobHandler} instances (which have
 * no public accessors) to assert that the connector instance, {@code DocumentFactory}, and {@code
 * ObjectMapper} wired into each physical tenant's job worker are genuinely isolated.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      TestPrototypeOutboundConnector.class,
      TestSingletonOutboundConnector.class
    },
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class MultiClientOutboundConnectorInstanceIsolationTest {

  @MockitoBean private JobWorkerManager jobWorkerManager;

  @Autowired private CamundaClientRegistry camundaClientRegistry;
  @Autowired private OutboundConnectorManager outboundConnectorManager;

  private CamundaClient clientA;
  private CamundaClient clientB;

  private JobHandler prototypeHandlerA;
  private JobHandler prototypeHandlerB;
  private JobHandler singletonHandlerA;
  private JobHandler singletonHandlerB;

  /**
   * Captures both physical tenants' job handlers once, up front: {@code @MockitoBean} resets the
   * mock's recorded invocations before each test method, so {@code verify(...).capture(...)} would
   * otherwise see zero interactions in every test after the first.
   */
  @BeforeAll
  void startBothPhysicalTenantsAndCaptureJobHandlers() {
    clientA = camundaClientRegistry.get("engine-a");
    clientB = camundaClientRegistry.get("engine-b");
    outboundConnectorManager.onStart(clientA, "engine-a");
    outboundConnectorManager.onStart(clientB, "engine-b");

    prototypeHandlerA = jobHandlerFor(clientA, TestPrototypeOutboundConnector.TYPE);
    prototypeHandlerB = jobHandlerFor(clientB, TestPrototypeOutboundConnector.TYPE);
    singletonHandlerA = jobHandlerFor(clientA, TestSingletonOutboundConnector.TYPE);
    singletonHandlerB = jobHandlerFor(clientB, TestSingletonOutboundConnector.TYPE);
  }

  /**
   * Finds the {@link ManagedJobWorker} that {@link OutboundConnectorManager} registered for the
   * given client and connector type, and builds the resulting {@link JobHandler} from its {@code
   * JobHandlerFactory} — mirroring what {@code JobWorkerManager} would normally do internally.
   */
  private JobHandler jobHandlerFor(CamundaClient client, String connectorType) {
    var clientCaptor = ArgumentCaptor.forClass(CamundaClient.class);
    var workerCaptor = ArgumentCaptor.forClass(ManagedJobWorker.class);
    verify(jobWorkerManager, atLeastOnce())
        .createJobWorker(clientCaptor.capture(), workerCaptor.capture(), any());

    var clients = clientCaptor.getAllValues();
    var workers = workerCaptor.getAllValues();
    for (int i = 0; i < clients.size(); i++) {
      if (clients.get(i) == client
          && workers.get(i).jobWorkerValue().getType().value().equals(connectorType)) {
        return workers
            .get(i)
            .jobHandlerFactory()
            .getJobHandler(new JobHandlerFactoryContext(workers.get(i).jobWorkerValue(), client));
      }
    }
    throw new AssertionError(
        "No job worker registered for client " + client + " and type " + connectorType);
  }

  private static Object privateField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  @Test
  void prototypeScopedConnectorGetsFreshInstancePerPhysicalTenant() throws Exception {
    var connectorA = privateField(prototypeHandlerA, "call");
    var connectorB = privateField(prototypeHandlerB, "call");

    assertThat(connectorA).isInstanceOf(TestPrototypeOutboundConnector.class);
    assertThat(connectorB).isInstanceOf(TestPrototypeOutboundConnector.class);
    assertThat(connectorA).isNotSameAs(connectorB);
  }

  @Test
  void singletonScopedConnectorAlsoGetsFreshInstancePerPhysicalTenant() throws Exception {
    // #6961: isolation must not require connector authors to opt into prototype scope, so even a
    // (default) singleton-scoped bean gets a distinct instance per physical tenant.
    var connectorA = privateField(singletonHandlerA, "call");
    var connectorB = privateField(singletonHandlerB, "call");

    assertThat(connectorA).isInstanceOf(TestSingletonOutboundConnector.class);
    assertThat(connectorB).isInstanceOf(TestSingletonOutboundConnector.class);
    assertThat(connectorA).isNotSameAs(connectorB);
  }

  @Test
  void documentFactoryDiffersPerPhysicalTenant() throws Exception {
    var documentFactoryA = privateField(singletonHandlerA, "documentFactory");
    var documentFactoryB = privateField(singletonHandlerB, "documentFactory");

    assertThat(documentFactoryA).isNotNull();
    assertThat(documentFactoryB).isNotNull();
    assertThat(documentFactoryA).isNotSameAs(documentFactoryB);
  }

  @Test
  void objectMapperDiffersPerPhysicalTenant() throws Exception {
    var objectMapperA = privateField(singletonHandlerA, "objectMapper");
    var objectMapperB = privateField(singletonHandlerB, "objectMapper");

    assertThat(objectMapperA).isNotNull();
    assertThat(objectMapperB).isNotNull();
    assertThat(objectMapperA).isNotSameAs(objectMapperB);
  }
}
