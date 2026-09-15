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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that {@link ImportSchedulers} polls every configured physical tenant's {@link
 * SearchQueryClient} independently, via the single shared {@link Importers} instance: one physical
 * tenant's failure must not prevent another's import from succeeding within the same scheduled
 * tick.
 */
class ImportSchedulersTest {

  private static ImportResult resultFor(String physicalTenantId) {
    var ref = new ProcessDefinitionRef(physicalTenantId, "process", "tenant1");
    return new ImportResult(Map.of(ref, Set.of(1L)), ImportType.LATEST_VERSIONS, physicalTenantId);
  }

  @Test
  void latestVersionImport_pollsEveryPhysicalTenant() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var clientA = mock(SearchQueryClient.class);
    var clientB = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", clientA))
        .thenReturn(resultFor("physical-tenant-a"));
    when(importers.importLatestVersions("physical-tenant-b", clientB))
        .thenReturn(resultFor("physical-tenant-b"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", clientA, "physical-tenant-b", clientB),
            importers,
            true);

    schedulers.scheduleLatestVersionImport();

    verify(stateManager).update(resultFor("physical-tenant-a"));
    verify(stateManager).update(resultFor("physical-tenant-b"));
    assertThat(schedulers.isReady()).isTrue();
  }

  @Test
  void oneTenantsImportFailure_doesNotPreventAnotherTenantsImport() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var failingClient = mock(SearchQueryClient.class);
    var healthyClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-failing", failingClient))
        .thenThrow(new RuntimeException("connection refused"));
    when(importers.importLatestVersions("physical-tenant-healthy", healthyClient))
        .thenReturn(resultFor("physical-tenant-healthy"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of(
                "physical-tenant-failing", failingClient, "physical-tenant-healthy", healthyClient),
            importers,
            true);

    schedulers.scheduleLatestVersionImport();

    // the healthy tenant's import must still have gone through despite the other's failure
    verify(stateManager).update(resultFor("physical-tenant-healthy"));
    verify(stateManager, times(0)).update(resultFor("physical-tenant-failing"));
    // overall readiness reflects that at least one tenant failed on this tick
    assertThat(schedulers.isReady()).isFalse();
  }

  @Test
  void readyFlag_recoversOnceAllTenantsSucceedAgain() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var client = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", client))
        .thenThrow(new RuntimeException("transient failure"))
        .thenReturn(resultFor("physical-tenant-a"));

    var schedulers =
        new ImportSchedulers(stateManager, Map.of("physical-tenant-a", client), importers, true);

    schedulers.scheduleLatestVersionImport();
    assertThat(schedulers.isReady()).isFalse();

    schedulers.scheduleLatestVersionImport();
    assertThat(schedulers.isReady()).isTrue();
  }

  @Test
  void activeVersionImport_pollsEveryPhysicalTenantIndependently() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var failingClient = mock(SearchQueryClient.class);
    var healthyClient = mock(SearchQueryClient.class);
    when(importers.importActiveVersions("physical-tenant-failing", failingClient))
        .thenThrow(new RuntimeException("connection refused"));
    when(importers.importActiveVersions("physical-tenant-healthy", healthyClient))
        .thenReturn(resultFor("physical-tenant-healthy"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of(
                "physical-tenant-failing", failingClient, "physical-tenant-healthy", healthyClient),
            importers,
            true);

    schedulers.scheduleActiveVersionImport();

    verify(stateManager).update(resultFor("physical-tenant-healthy"));
    assertThat(schedulers.isReady()).isFalse();
  }

  @Test
  void activeVersionImport_skipsAllTenants_whenPollingDisabled() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var client = mock(SearchQueryClient.class);

    var schedulers =
        new ImportSchedulers(stateManager, Map.of("physical-tenant-a", client), importers, false);

    schedulers.scheduleActiveVersionImport();

    verify(importers, times(0)).importActiveVersions(any(), any());
    verify(stateManager, times(0)).update(any());
  }

  @Test
  void onStart_registersNewClientForPolling() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var camundaClient = clientWithPhysicalTenantId("physical-tenant-a");
    when(importers.importLatestVersions(any(), any())).thenReturn(resultFor("physical-tenant-a"));
    var schedulers = new ImportSchedulers(stateManager, Map.of(), importers, true);

    schedulers.onStart(camundaClient, "client-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers).importLatestVersions(eq("physical-tenant-a"), any(SearchQueryClient.class));
    verify(stateManager).update(resultFor("physical-tenant-a"));
  }

  @Test
  void onStart_migratesFallbackRegistrationToResolvedPhysicalTenantId() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var overrideSearchQueryClient = mock(SearchQueryClient.class);
    var camundaClient = clientWithPhysicalTenantId("physical-tenant-a");
    when(importers.importLatestVersions("physical-tenant-a", overrideSearchQueryClient))
        .thenReturn(resultFor("physical-tenant-a"));
    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("client-a", overrideSearchQueryClient),
            Optional.of(overrideSearchQueryClient),
            200,
            importers,
            true);

    schedulers.onStart(camundaClient, "client-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers).importLatestVersions("physical-tenant-a", overrideSearchQueryClient);
    verify(importers, never()).importLatestVersions(eq("client-a"), any());
  }

  @Test
  void onStart_rejectsDuplicatePhysicalTenantIdFromDifferentClientName() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var firstClient = clientWithPhysicalTenantId("shared-physical-tenant");
    var duplicateClient = clientWithPhysicalTenantId("shared-physical-tenant");
    var schedulers = new ImportSchedulers(stateManager, Map.of(), importers, true);

    schedulers.onStart(firstClient, "client-a");

    assertThatThrownBy(() -> schedulers.onStart(duplicateClient, "client-b"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shared-physical-tenant")
        .hasMessageContaining("client-b");
  }

  @Test
  void onStart_replacesSearchClientAfterReconnect() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var oldClient = clientWithPhysicalTenantId("physical-tenant-a");
    var replacementClient = clientWithPhysicalTenantId("physical-tenant-a");
    when(importers.importLatestVersions(any(), any())).thenReturn(resultFor("physical-tenant-a"));
    var schedulers = new ImportSchedulers(stateManager, Map.of(), importers, true);
    var searchClientCaptor = ArgumentCaptor.forClass(SearchQueryClient.class);

    schedulers.onStart(oldClient, "client-a");
    schedulers.scheduleLatestVersionImport();
    schedulers.onStart(replacementClient, "client-a");
    schedulers.onStop(oldClient, "client-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers, times(2))
        .importLatestVersions(eq("physical-tenant-a"), searchClientCaptor.capture());
    assertThat(searchClientCaptor.getAllValues().get(1))
        .isNotSameAs(searchClientCaptor.getAllValues().get(0));
  }

  @Test
  void onStop_removesClientFromPolling() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var camundaClient = clientWithPhysicalTenantId("physical-tenant-a");
    when(camundaClient.getConfiguration().getPhysicalTenantId())
        .thenReturn("physical-tenant-a")
        .thenThrow(new IllegalStateException("client is closing"));
    var schedulers = new ImportSchedulers(stateManager, Map.of(), importers, true);

    schedulers.onStart(camundaClient, "client-a");
    schedulers.onStop(camundaClient, "client-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers, never()).importLatestVersions(any(), any());
    assertThat(schedulers.isReady()).isTrue();
  }

  @Test
  void reconnect_preservesLegacySearchQueryClientOverride() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var overrideSearchQueryClient = mock(SearchQueryClient.class);
    var camundaClient = clientWithPhysicalTenantId("physical-tenant-a");
    when(importers.importLatestVersions("physical-tenant-a", overrideSearchQueryClient))
        .thenReturn(resultFor("physical-tenant-a"));
    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", overrideSearchQueryClient),
            Optional.of(overrideSearchQueryClient),
            200,
            importers,
            true);

    schedulers.onStart(camundaClient, "client-a");
    schedulers.onStop(camundaClient, "client-a");
    schedulers.onStart(camundaClient, "client-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers).importLatestVersions("physical-tenant-a", overrideSearchQueryClient);
  }

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }
}
