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

import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Utility class for schedulers used to import process data needed for inbound connectors. */
public class ImportSchedulers {

  private static final Logger LOG = LoggerFactory.getLogger(ImportSchedulers.class);

  private final ProcessStateManager stateStore;
  private final Importers importers;

  private boolean ready = true;

  private final boolean activeVersionsPollingEnabled;

  public ImportSchedulers(
      ProcessStateManager stateStore, Importers importers, boolean activeVersionsPollingEnabled) {
    this.activeVersionsPollingEnabled = activeVersionsPollingEnabled;
    this.stateStore = stateStore;
    this.importers = importers;
  }

  @Scheduled(fixedDelayString = "${camunda.connector.polling.interval:5000}")
  public void scheduleLatestVersionImport() {
    try {
      var result = importers.importLatestVersions();
      stateStore.update(result);
      ready = true;
    } catch (Exception e) {
      LOG.error("Failed to import LATEST process versions", e);
      ready = false;
    }
  }

  @Scheduled(fixedDelayString = "${camunda.connector.polling.interval:5000}")
  public void scheduleActiveVersionImport() {
    if (!activeVersionsPollingEnabled) {
      LOG.debug("Skipping active versions polling.");
      return;
    }
    try {
      var result = importers.importActiveVersions();
      stateStore.update(result);
      ready = true;
    } catch (Exception e) {
      LOG.error("Failed to import ACTIVE process versions", e);
      ready = false;
    }
  }

  public boolean isReady() {
    return ready;
  }
}
