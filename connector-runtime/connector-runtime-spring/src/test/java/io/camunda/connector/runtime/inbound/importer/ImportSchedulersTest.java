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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
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
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ImportSchedulers} polls every configured physical tenant's {@link
 * SearchQueryClient} independently, via the single shared {@link Importers} instance: one physical
 * tenant's failure must not prevent another's import from succeeding within the same scheduled
 * tick.
 */
class ImportSchedulersTest {

  /**
   * Fails loudly rather than returning null: the tests below that pass it never fire a lifecycle
   * event, so nothing may rebuild a search client behind their backs.
   */
  private static final Function<CamundaClient, SearchQueryClient> UNUSED_CLIENT_FACTORY =
      client -> {
        throw new AssertionError("no SearchQueryClient should be rebuilt without a client restart");
      };

  private static CamundaClient camundaClientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

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
            UNUSED_CLIENT_FACTORY,
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
            UNUSED_CLIENT_FACTORY,
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
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", client),
            UNUSED_CLIENT_FACTORY,
            importers,
            true);

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
            UNUSED_CLIENT_FACTORY,
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
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", client),
            UNUSED_CLIENT_FACTORY,
            importers,
            false);

    schedulers.scheduleActiveVersionImport();

    verify(importers, times(0)).importActiveVersions(any(), any());
    verify(stateManager, times(0)).update(any());
  }

  @Test
  void onStart_pollsTheRestartedClientInsteadOfTheOneCapturedAtStartup() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var staleClient = mock(SearchQueryClient.class);
    var restartedClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", restartedClient))
        .thenReturn(resultFor("physical-tenant-a"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", staleClient),
            client -> restartedClient,
            importers,
            true);

    schedulers.onStart(camundaClientWithPhysicalTenantId("physical-tenant-a"), "engine-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers, times(0)).importLatestVersions("physical-tenant-a", staleClient);
    verify(stateManager).update(resultFor("physical-tenant-a"));
    assertThat(schedulers.isReady()).isTrue();
  }

  @Test
  void onStart_refreshesTheStartupKey_whenTheClientHasNoPhysicalTenantIdOfItsOwn() {
    // The startup snapshot keys a client by its own name whenever the client reports no
    // physical-tenant-id (or its configuration could not be read yet). A later start event must
    // refresh that entry rather than file the same client under a second key, since every other
    // per-physical-tenant map in the runtime is keyed the startup way and stays that way.
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var restartedClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("default", restartedClient))
        .thenReturn(resultFor("default"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("default", mock(SearchQueryClient.class)),
            client -> restartedClient,
            importers,
            true);

    schedulers.onStart(camundaClientWithPhysicalTenantId(null), "default");
    schedulers.scheduleLatestVersionImport();

    verify(stateManager).update(resultFor("default"));
    verify(importers, times(1)).importLatestVersions(any(), any());
  }

  @Test
  void onStop_stopsPollingThatPhysicalTenantOnly() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var clientA = mock(SearchQueryClient.class);
    var clientB = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-b", clientB))
        .thenReturn(resultFor("physical-tenant-b"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", clientA, "physical-tenant-b", clientB),
            UNUSED_CLIENT_FACTORY,
            importers,
            true);

    schedulers.onStop(camundaClientWithPhysicalTenantId("physical-tenant-a"), "engine-a");
    schedulers.scheduleLatestVersionImport();

    verify(importers, times(0)).importLatestVersions("physical-tenant-a", clientA);
    verify(stateManager).update(resultFor("physical-tenant-b"));
    // a client that was deliberately stopped must not be reported as an import failure
    assertThat(schedulers.isReady()).isTrue();
  }

  @Test
  void onStart_afterOnStop_resumesPollingThatPhysicalTenant() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var restartedClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", restartedClient))
        .thenReturn(resultFor("physical-tenant-a"));
    var camundaClient = camundaClientWithPhysicalTenantId("physical-tenant-a");

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", mock(SearchQueryClient.class)),
            client -> restartedClient,
            importers,
            true);

    schedulers.onStop(camundaClient, "engine-a");
    schedulers.scheduleLatestVersionImport();
    verify(stateManager, times(0)).update(any());

    schedulers.onStart(camundaClient, "engine-a");
    schedulers.scheduleLatestVersionImport();
    verify(stateManager).update(resultFor("physical-tenant-a"));
  }

  @Test
  void lifecycleEvents_areIgnoredForAPhysicalTenantThisRuntimeIsNotConfiguredFor() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var clientA = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", clientA))
        .thenReturn(resultFor("physical-tenant-a"));
    var unknownClient = camundaClientWithPhysicalTenantId("physical-tenant-unknown");

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", clientA),
            UNUSED_CLIENT_FACTORY,
            importers,
            true);

    // neither event may add a key the rest of the runtime knows nothing about, nor drop the one
    // configured tenant; UNUSED_CLIENT_FACTORY additionally asserts no client is built
    schedulers.onStart(unknownClient, "engine-x");
    schedulers.onStop(unknownClient, "engine-x");
    schedulers.scheduleLatestVersionImport();

    verify(stateManager).update(resultFor("physical-tenant-a"));
    verify(importers, times(1)).importLatestVersions(any(), any());
  }
}
