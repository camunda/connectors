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
import io.camunda.connector.runtime.inbound.search.SearchQueryClientImpl;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Utility class for schedulers used to import process data needed for inbound connectors. */
public class ImportSchedulers implements CamundaClientLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(ImportSchedulers.class);

  private final ProcessStateManager stateStore;
  private final Map<String, ClientRegistration> clientsByPhysicalTenantId;
  private final Importers importers;
  private final ExecutorService executor;
  private final Optional<SearchQueryClient> legacySearchQueryClient;
  private final Optional<String> legacySearchQueryClientPhysicalTenantId;
  private final int limit;

  private volatile boolean ready = true;

  private final boolean activeVersionsPollingEnabled;

  private record ClientRegistration(
      Optional<CamundaClient> camundaClient, SearchQueryClient searchQueryClient) {}

  public ImportSchedulers(
      ProcessStateManager stateStore,
      Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId,
      Importers importers,
      boolean activeVersionsPollingEnabled) {
    this(
        stateStore,
        searchQueryClientsByPhysicalTenantId,
        Optional.empty(),
        200,
        importers,
        activeVersionsPollingEnabled);
  }

  public ImportSchedulers(
      ProcessStateManager stateStore,
      Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId,
      Optional<SearchQueryClient> legacySearchQueryClient,
      int limit,
      Importers importers,
      boolean activeVersionsPollingEnabled) {
    this.activeVersionsPollingEnabled = activeVersionsPollingEnabled;
    this.stateStore = stateStore;
    this.clientsByPhysicalTenantId = new ConcurrentHashMap<>();
    searchQueryClientsByPhysicalTenantId.forEach(
        (physicalTenantId, client) ->
            clientsByPhysicalTenantId.put(
                physicalTenantId, new ClientRegistration(Optional.empty(), client)));
    this.legacySearchQueryClient = legacySearchQueryClient;
    this.legacySearchQueryClientPhysicalTenantId =
        searchQueryClientsByPhysicalTenantId.entrySet().stream()
            .filter(
                entry ->
                    legacySearchQueryClient
                        .map(searchQueryClient -> entry.getValue() == searchQueryClient)
                        .orElse(false))
            .map(Map.Entry::getKey)
            .findFirst();
    this.limit = limit;
    this.importers = importers;
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public void onStart(CamundaClient client) {
    onStart(client, "default");
  }

  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  @Override
  public void onStart(CamundaClient client, String clientName) {
    var physicalTenantId = PhysicalTenantIds.resolvePhysicalTenantId(client, clientName);
    var searchQueryClient =
        legacySearchQueryClientPhysicalTenantId
            .filter(physicalTenantId::equals)
            .flatMap(ignored -> legacySearchQueryClient)
            .orElseGet(() -> new SearchQueryClientImpl(client, limit));
    clientsByPhysicalTenantId.put(
        physicalTenantId, new ClientRegistration(Optional.of(client), searchQueryClient));
  }

  @Override
  public void onStop(CamundaClient client, String clientName) {
    var matchingRegistration =
        clientsByPhysicalTenantId.entrySet().stream()
            .filter(
                entry ->
                    entry
                        .getValue()
                        .camundaClient()
                        .filter(registeredClient -> registeredClient == client)
                        .isPresent())
            .findFirst();
    if (matchingRegistration.isPresent()) {
      var entry = matchingRegistration.orElseThrow();
      clientsByPhysicalTenantId.remove(entry.getKey(), entry.getValue());
      return;
    }

    var physicalTenantId = PhysicalTenantIds.resolvePhysicalTenantId(client, clientName);
    clientsByPhysicalTenantId.computeIfPresent(
        physicalTenantId,
        (ignored, registration) -> registration.camundaClient().isEmpty() ? null : registration);
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
   * Polls every configured physical tenant concurrently, so that one tenant stalling (e.g. a
   * connection attempt that hangs until timeout) does not delay the others from starting and
   * completing within the same scheduled tick.
   */
  private boolean pollAllPhysicalTenants(
      ImportType importType, BiFunction<String, SearchQueryClient, ImportResult> importFn) {
    List<CompletableFuture<Boolean>> futures =
        clientsByPhysicalTenantId.entrySet().stream()
            .map(
                entry ->
                    CompletableFuture.supplyAsync(
                        () ->
                            pollOnePhysicalTenant(
                                importType,
                                importFn,
                                entry.getKey(),
                                entry.getValue().searchQueryClient()),
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

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
  }
}
