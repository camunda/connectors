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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
}
