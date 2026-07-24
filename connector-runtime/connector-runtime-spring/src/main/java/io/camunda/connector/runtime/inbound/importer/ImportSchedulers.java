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

import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Utility class for schedulers used to import process data needed for inbound connectors. */
public class ImportSchedulers {

  private static final Logger LOG = LoggerFactory.getLogger(ImportSchedulers.class);

  private final ProcessStateManager stateStore;
  private final Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId;
  private final Importers importers;
  private final ExecutorService executor;

  private volatile boolean ready = true;

  private final boolean activeVersionsPollingEnabled;

  public ImportSchedulers(
      ProcessStateManager stateStore,
      Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId,
      Importers importers,
      boolean activeVersionsPollingEnabled) {
    this.activeVersionsPollingEnabled = activeVersionsPollingEnabled;
    this.stateStore = stateStore;
    this.searchQueryClientsByPhysicalTenantId = searchQueryClientsByPhysicalTenantId;
    this.importers = importers;
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
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
        searchQueryClientsByPhysicalTenantId.entrySet().stream()
            .map(
                entry ->
                    CompletableFuture.supplyAsync(
                        () -> pollOnePhysicalTenant(importType, importFn, entry), executor))
            .toList();
    return futures.stream().map(CompletableFuture::join).reduce(true, Boolean::logicalAnd);
  }

  private boolean pollOnePhysicalTenant(
      ImportType importType,
      BiFunction<String, SearchQueryClient, ImportResult> importFn,
      Map.Entry<String, SearchQueryClient> entry) {
    try {
      var result = importFn.apply(entry.getKey(), entry.getValue());
      stateStore.update(result);
      return true;
    } catch (Exception e) {
      LOG.error(
          "Failed to import {} process versions for physical tenant '{}'",
          importType,
          entry.getKey(),
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
