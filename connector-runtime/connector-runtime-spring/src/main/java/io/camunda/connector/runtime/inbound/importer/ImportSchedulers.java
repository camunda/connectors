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

import io.camunda.client.CamundaClient;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.connector.runtime.inbound.PhysicalTenantIds;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Utility class for schedulers used to import process data needed for inbound connectors. */
public class ImportSchedulers implements CamundaClientLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(ImportSchedulers.class);

  private final ProcessStateManager stateStore;

  /**
   * The physical tenant ID each configured client name was filed under at startup. Deliberately
   * frozen: every other per-physical-tenant map in the runtime ({@code
   * PhysicalTenantIdRoutingInboundConnectorContextFactory}'s delegates, {@code
   * ProcessDefinitionInspector}'s search clients, the document factories {@code
   * OutboundConnectorManager} looks up) is built at startup and keyed the same way, and imported
   * process definitions are tagged with these keys. A lifecycle event may therefore only refresh
   * the client behind an existing key, never introduce or rename one — otherwise polling would
   * start tagging imports with an ID no other component knows about.
   *
   * <p>Keyed by client name rather than by physical tenant ID so that an event addresses the entry
   * its own client was given at startup, instead of whatever ID that client resolves to at event
   * time. See {@link PhysicalTenantIds#buildPhysicalTenantIdByClientName} for why re-resolving is
   * not equivalent.
   */
  private final Map<String, String> physicalTenantIdByClientName;

  /**
   * The search client currently polled per physical tenant. Mutable, and a subset of {@link
   * #physicalTenantIdByClientName}'s values: a tenant whose client has stopped is removed until
   * that client starts again, so polling skips it instead of querying a closed client.
   */
  private final Map<String, SearchQueryClient> activeSearchQueryClientsByPhysicalTenantId;

  private final Function<CamundaClient, SearchQueryClient> searchQueryClientFactory;
  private final Importers importers;
  private final ExecutorService executor;

  private volatile boolean ready = true;

  private final boolean activeVersionsPollingEnabled;

  public ImportSchedulers(
      ProcessStateManager stateStore,
      Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId,
      Map<String, String> physicalTenantIdByClientName,
      Function<CamundaClient, SearchQueryClient> searchQueryClientFactory,
      Importers importers,
      boolean activeVersionsPollingEnabled) {
    this.activeVersionsPollingEnabled = activeVersionsPollingEnabled;
    this.stateStore = stateStore;
    this.physicalTenantIdByClientName = Map.copyOf(physicalTenantIdByClientName);
    this.activeSearchQueryClientsByPhysicalTenantId =
        new ConcurrentHashMap<>(searchQueryClientsByPhysicalTenantId);
    this.searchQueryClientFactory = searchQueryClientFactory;
    this.importers = importers;
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * Required by {@link CamundaClientLifecycleAware}; in practice unreachable in a Spring context,
   * since {@code CamundaClientEventListener} always invokes the 2-arg overload instead. Mirrors
   * {@code OutboundConnectorManager}'s handling of the same pair.
   */
  @Override
  public void onStart(CamundaClient client) {
    onStart(client, "default");
  }

  /** See {@link #onStart(CamundaClient)}. */
  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  /**
   * Replaces this physical tenant's search client with one backed by the client instance the
   * lifecycle event carries, so a tenant whose client was replaced (or whose client had not been
   * usable yet when the startup snapshot was taken) is polled through the current client instead of
   * the one captured at bean construction.
   *
   * <p>The other startup-snapshotted consumers of a per-physical-tenant {@code SearchQueryClient}
   * map — notably {@code ProcessDefinitionInspector}, which the {@link ProcessStateManager} below
   * fetches BPMN models through — deliberately need no equivalent refresh. Two separate mechanisms
   * cover the two paths that can deliver an event, and only the first is an identity guarantee:
   *
   * <ul>
   *   <li><b>Production:</b> the event's client is literally the same instance. {@code
   *       MultiCamundaLifecycleEventProducer} publishes {@code registry.get(name)}, which is
   *       exactly what {@code PhysicalTenantIds.resolveClient} snapshotted. Pinned by {@code
   *       theProducerPublishesTheSameClientInstanceTheStartupSnapshotsHold}, which fails if the
   *       producer ever begins publishing a different instance.
   *   <li><b>{@code camunda-process-test-spring}:</b> the instances differ — the event carries the
   *       raw per-test client, while the snapshot holds the {@code CamundaClientProxy} bean — but
   *       they reach the same connection, because the test listener sets that proxy's delegate to
   *       this very client before publishing. The snapshotted reference is the proxy, so it follows
   *       each per-test swap by itself rather than pinning one client.
   * </ul>
   */
  @Override
  public void onStart(CamundaClient client, String clientName) {
    var physicalTenantId = physicalTenantIdByClientName.get(clientName);
    if (physicalTenantId == null) {
      logUnknownClientName("start", clientName);
      return;
    }
    activeSearchQueryClientsByPhysicalTenantId.put(
        physicalTenantId, searchQueryClientFactory.apply(client));
  }

  /**
   * Stops polling this physical tenant until its client starts again. Resolved exactly like {@link
   * #onStart(CamundaClient, String)} so that a start/stop pair for the same client always addresses
   * the same entry and cannot leak one.
   */
  @Override
  public void onStop(CamundaClient client, String clientName) {
    var physicalTenantId = physicalTenantIdByClientName.get(clientName);
    if (physicalTenantId == null) {
      logUnknownClientName("stop", clientName);
      return;
    }
    activeSearchQueryClientsByPhysicalTenantId.remove(physicalTenantId);
  }

  /**
   * Logs and ignores rather than throwing: {@code CamundaClientEventListener} fans out over every
   * {@code CamundaClientLifecycleAware} bean in an unguarded {@code forEach}, so throwing here
   * would also skip every bean processed after this one. Ignoring leaves the startup snapshot in
   * place, i.e. degrades to the behaviour this class had before it tracked lifecycle events.
   */
  private void logUnknownClientName(String event, String clientName) {
    LOG.warn(
        "Ignoring {} event for CamundaClient '{}': it is not one of the clients configured for"
            + " process definition polling {}. Polling is left unchanged.",
        event,
        clientName,
        physicalTenantIdByClientName.keySet());
  }

  @Scheduled(
      fixedDelayString = "${camunda.connector.polling.interval:5000}",
      initialDelayString = "${camunda.connector.polling.initial-delay:0}")
  public void scheduleLatestVersionImport() {
    ready = pollAllPhysicalTenants(ImportType.LATEST_VERSIONS, importers::importLatestVersions);
  }

  @Scheduled(
      fixedDelayString = "${camunda.connector.polling.interval:5000}",
      initialDelayString = "${camunda.connector.polling.initial-delay:0}")
  public void scheduleActiveVersionImport() {
    if (!activeVersionsPollingEnabled) {
      LOG.debug("Skipping active versions polling.");
      return;
    }
    ready =
        pollAllPhysicalTenants(
            ImportType.HAVE_ACTIVE_SUBSCRIPTIONS, importers::importActiveVersions);
  }

  /**
   * Polls every physical tenant with a currently started client concurrently, so that one tenant
   * stalling (e.g. a connection attempt that hangs until timeout) does not delay the others from
   * starting and completing within the same scheduled tick.
   */
  private boolean pollAllPhysicalTenants(
      ImportType importType, BiFunction<String, SearchQueryClient, ImportResult> importFn) {
    List<CompletableFuture<Boolean>> futures =
        activeSearchQueryClientsByPhysicalTenantId.entrySet().stream()
            .map(
                entry ->
                    CompletableFuture.supplyAsync(
                        () ->
                            pollOnePhysicalTenant(
                                importType, importFn, entry.getKey(), entry.getValue()),
                        executor))
            .toList();
    return futures.stream().map(CompletableFuture::join).reduce(true, Boolean::logicalAnd);
  }

  private boolean pollOnePhysicalTenant(
      ImportType importType,
      BiFunction<String, SearchQueryClient, ImportResult> importFn,
      String physicalTenantId,
      SearchQueryClient searchQueryClient) {
    try {
      var result = importFn.apply(physicalTenantId, searchQueryClient);
      stateStore.update(result);
      return true;
    } catch (Exception e) {
      LOG.error(
          "Failed to import {} process versions for physical tenant '{}'",
          importType,
          physicalTenantId,
          e);
      return false;
    }
  }

  public boolean isReady() {
    return ready;
  }

  /** The physical tenants currently polled, i.e. those whose {@link CamundaClient} is started. */
  Set<String> activePhysicalTenantIds() {
    return Set.copyOf(activeSearchQueryClientsByPhysicalTenantId.keySet());
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
  }
}
