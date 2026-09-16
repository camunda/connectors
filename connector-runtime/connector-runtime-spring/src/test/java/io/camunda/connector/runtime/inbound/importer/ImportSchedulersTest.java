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

  /** For the polling-only tests, whose snapshot keys double as their client names. */
  private static final Map<String, String> IDENTITY_CLIENT_NAMES =
      Map.of(
          "physical-tenant-a", "physical-tenant-a",
          "physical-tenant-b", "physical-tenant-b",
          "physical-tenant-failing", "physical-tenant-failing",
          "physical-tenant-healthy", "physical-tenant-healthy");

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
            IDENTITY_CLIENT_NAMES,
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
            IDENTITY_CLIENT_NAMES,
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
            IDENTITY_CLIENT_NAMES,
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
            IDENTITY_CLIENT_NAMES,
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
            IDENTITY_CLIENT_NAMES,
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
            Map.of("engine-a", "physical-tenant-a"),
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
  void onStart_refreshesTheStartupFallbackKey_evenOnceTheClientResolvesARealTenantId() {
    // R2: startup could not read the configuration, so the entry is keyed by the client name.
    // Once the client becomes readable it reports a real, DIFFERENT physical tenant ID — the event
    // must still refresh the frozen "engine-c" entry. Resolving the ID from the event's client
    // instead would miss it, leaving the unusable startup client polled forever.
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var restartedClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("engine-c", restartedClient))
        .thenReturn(resultFor("engine-c"));

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("engine-c", mock(SearchQueryClient.class)),
            Map.of("engine-c", "engine-c"),
            client -> restartedClient,
            importers,
            true);

    schedulers.onStart(camundaClientWithPhysicalTenantId("resolved-tenant"), "engine-c");
    schedulers.scheduleLatestVersionImport();

    verify(stateManager).update(resultFor("engine-c"));
    assertThat(schedulers.activePhysicalTenantIds()).containsExactly("engine-c");

    // and the matching stop must find the same entry rather than being ignored
    schedulers.onStop(camundaClientWithPhysicalTenantId("resolved-tenant"), "engine-c");
    assertThat(schedulers.activePhysicalTenantIds()).isEmpty();
  }

  @Test
  void lifecycleEvents_cannotTakeOverAnotherClientsPhysicalTenant() {
    // R1/T1b: engine-b's configuration was unreadable at startup, so it was keyed by name and the
    // startup duplicate-physical-tenant-id guard never ran for it. Its client now resolves to
    // "tenanta", which engine-a legitimately owns. The event must address engine-b's own entry —
    // resolving the ID from the client would hand it engine-a's, and the matching stop would then
    // orphan engine-a permanently in a live runtime.
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var engineAClient = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("tenanta", engineAClient)).thenReturn(resultFor("tenanta"));
    var engineBCamundaClient = camundaClientWithPhysicalTenantId("tenanta");

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("tenanta", engineAClient, "engine-b", mock(SearchQueryClient.class)),
            Map.of("engine-a", "tenanta", "engine-b", "engine-b"),
            client -> mock(SearchQueryClient.class),
            importers,
            true);

    schedulers.onStart(engineBCamundaClient, "engine-b");
    schedulers.onStop(engineBCamundaClient, "engine-b");

    // engine-a's tenant survives engine-b's whole start/stop cycle untouched
    assertThat(schedulers.activePhysicalTenantIds()).containsExactly("tenanta");
    schedulers.scheduleLatestVersionImport();
    verify(stateManager).update(resultFor("tenanta"));
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
            Map.of("default", "default"),
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
            Map.of("engine-a", "physical-tenant-a", "engine-b", "physical-tenant-b"),
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
            Map.of("engine-a", "physical-tenant-a"),
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
  void lifecycleEvents_areIgnoredForAClientThisRuntimeIsNotConfiguredFor() {
    var stateManager = mock(ProcessStateManager.class);
    var importers = mock(Importers.class);
    var clientA = mock(SearchQueryClient.class);
    when(importers.importLatestVersions("physical-tenant-a", clientA))
        .thenReturn(resultFor("physical-tenant-a"));
    // resolves onto the configured tenant, so only the unknown client NAME can reject it
    var unknownClient = camundaClientWithPhysicalTenantId("physical-tenant-a");

    var schedulers =
        new ImportSchedulers(
            stateManager,
            Map.of("physical-tenant-a", clientA),
            Map.of("engine-a", "physical-tenant-a"),
            UNUSED_CLIENT_FACTORY,
            importers,
            true);

    // neither event may touch the configured tenant's entry; UNUSED_CLIENT_FACTORY additionally
    // asserts no client is built
    schedulers.onStart(unknownClient, "engine-x");
    schedulers.onStop(unknownClient, "engine-x");
    schedulers.scheduleLatestVersionImport();

    verify(stateManager).update(resultFor("physical-tenant-a"));
    verify(importers, times(1)).importLatestVersions(any(), any());
  }
}
