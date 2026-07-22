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
package io.camunda.connector.runtime.outbound.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OutboundConnectorManagerTest {

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  private static OutboundConnectorConfiguration connectorConfig(
      String type, Supplier<OutboundConnectorFunction> instanceSupplier) {
    return new OutboundConnectorConfiguration(type, new String[0], type, instanceSupplier, null);
  }

  private static OutboundConnectorManager managerWith(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId,
      Map<String, SecretFilterFactory> secretFilterFactoriesByPhysicalTenantId) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        mock(JobCallbackCommandWrapperFactory.class),
        mock(SecretProviderAggregator.class),
        mock(ValidationProvider.class),
        documentFactoriesByPhysicalTenantId,
        mock(ObjectMapper.class),
        mock(MetricsRecorder.class),
        secretFilterFactoriesByPhysicalTenantId,
        null);
  }

  @Test
  void onStart_throwsClearErrorWhenResolvedPhysicalTenantHasNoConfiguredFactories() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations()).thenReturn(Set.of());
    var manager = managerWith(jobWorkerManager, connectorFactory, Map.of(), Map.of());
    var client = clientWithPhysicalTenantId("unconfigured-tenant");

    assertThatThrownBy(() -> manager.onStart(client, "engine-a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unconfigured-tenant");
  }

  @Test
  void onStart_opensOneWorkerPerActiveConnector_whenPhysicalTenantIsConfigured() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations())
        .thenReturn(
            List.of(
                connectorConfig("type-a", () -> mock(OutboundConnectorFunction.class)),
                connectorConfig("type-b", () -> mock(OutboundConnectorFunction.class))));
    var documentFactory = mock(DocumentFactory.class);
    var secretFilterFactory = mock(SecretFilterFactory.class);
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("tenant-a", documentFactory),
            Map.of("tenant-a", secretFilterFactory));
    var client = clientWithPhysicalTenantId("tenant-a");

    manager.onStart(client, "engine-a");

    verify(jobWorkerManager, times(2)).createJobWorker(any(), any(), any());
  }

  @Test
  void onStart_resolvesPhysicalTenantIdFromConfiguration_fallsBackToClientNameWhenNotConfigured() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations()).thenReturn(Set.of());
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("engine-b", mock(DocumentFactory.class)),
            Map.of("engine-b", mock(SecretFilterFactory.class)));
    // no physical-tenant-id explicitly configured -> falls back to the client name "engine-b"
    var client = clientWithPhysicalTenantId(null);

    manager.onStart(client, "engine-b");

    // no exception thrown proves resolution correctly fell back to "engine-b" and found the map
    // entries
  }

  @Test
  void onStart_fallsBackToClientNameWhenConfigurationCannotBeRead() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations()).thenReturn(Set.of());
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("engine-c", mock(DocumentFactory.class)),
            Map.of("engine-c", mock(SecretFilterFactory.class)));
    var uninitializedClient = mock(CamundaClient.class);
    when(uninitializedClient.getConfiguration())
        .thenThrow(new RuntimeException("client not initialized"));

    manager.onStart(uninitializedClient, "engine-c");

    // no exception thrown proves resolution fell back to "engine-c" despite the config read failure
  }

  @Test
  void onStart_createsOneConnectorInstancePerPhysicalTenant_isolatingAcrossTenants() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    var instancesCreated = new java.util.concurrent.atomic.AtomicInteger();
    Supplier<OutboundConnectorFunction> instanceSupplier =
        () -> {
          instancesCreated.incrementAndGet();
          return mock(OutboundConnectorFunction.class);
        };
    when(connectorFactory.getActiveConfigurations())
        .thenReturn(List.of(connectorConfig("type-a", instanceSupplier)));
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of(
                "tenant-a", mock(DocumentFactory.class),
                "tenant-b", mock(DocumentFactory.class)),
            Map.of(
                "tenant-a", mock(SecretFilterFactory.class),
                "tenant-b", mock(SecretFilterFactory.class)));

    manager.onStart(clientWithPhysicalTenantId("tenant-a"), "engine-a");
    manager.onStart(clientWithPhysicalTenantId("tenant-b"), "engine-b");

    assertThat(instancesCreated.get()).isEqualTo(2);
  }

  @Test
  void onStart_reusesSameConnectorInstance_acrossRepeatedOnStartForTheSamePhysicalTenant() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    var instancesCreated = new java.util.concurrent.atomic.AtomicInteger();
    Supplier<OutboundConnectorFunction> instanceSupplier =
        () -> {
          instancesCreated.incrementAndGet();
          return mock(OutboundConnectorFunction.class);
        };
    when(connectorFactory.getActiveConfigurations())
        .thenReturn(List.of(connectorConfig("type-a", instanceSupplier)));
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("tenant-a", mock(DocumentFactory.class)),
            Map.of("tenant-a", mock(SecretFilterFactory.class)));
    var client = clientWithPhysicalTenantId("tenant-a");

    manager.onStart(client, "engine-a");
    // simulate a client reconnect: onStop then onStart again for the same physical tenant
    manager.onStop(client, "engine-a");
    manager.onStart(client, "engine-a");

    assertThat(instancesCreated.get()).isEqualTo(1);
  }

  @Test
  void onStop_closesOnlyThisClientsJobWorkers_neverAllClients() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    var manager = managerWith(jobWorkerManager, connectorFactory, Map.of(), Map.of());
    var client = clientWithPhysicalTenantId("tenant-a");

    manager.onStop(client, "engine-a");

    verify(jobWorkerManager).closeJobWorkers(manager, client);
    verify(jobWorkerManager, never()).closeAllJobWorkers(any());
  }

  @Test
  void singleArgOnStartAndOnStop_delegateToTwoArgFormsWithDefaultClientName() {
    var jobWorkerManager = mock(JobWorkerManager.class);
    var connectorFactory = mock(OutboundConnectorFactory.class);
    when(connectorFactory.getActiveConfigurations()).thenReturn(Set.of());
    var manager =
        managerWith(
            jobWorkerManager,
            connectorFactory,
            Map.of("default", mock(DocumentFactory.class)),
            Map.of("default", mock(SecretFilterFactory.class)));
    var client = clientWithPhysicalTenantId(null);

    manager.onStart(client);
    manager.onStop(client);

    // resolves to "default" (client name fallback) and finds the map entries without throwing
    verify(jobWorkerManager).closeJobWorkers(manager, client);
  }
}
