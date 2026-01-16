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
package io.camunda.connector.runtime.inbound.state;

import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult.ProcessDefinitionRefAndKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe due to a synchronized method (simple but ok for the intended usage pattern). */
@ThreadSafe
public class ProcessStateContainerImpl implements ProcessStateContainer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessStateContainerImpl.class);

  /**
   * Internal state map. Keys: process definition IDs (tenant-aware) Values: maps of process
   * definition keys to their mutable state
   */
  private final Map<ProcessDefinitionRef, Map<Long, MutableProcessVersionState>> processStates =
      new HashMap<>();

  @Override
  public synchronized StateUpdateResult compareAndUpdate(ImportResult importResult) {
    final Set<ProcessDefinitionRefAndKey> toActivate = new HashSet<>();
    final Set<ProcessDefinitionRefAndKey> toDeactivate = new HashSet<>();

    // Track all imported processDefinitionIds
    Set<ProcessDefinitionRef> importedProcessIds =
        importResult.processDefinitionKeysByProcessId().keySet();

    // First, process all imported processDefinitionIds as before
    for (var importEntry : importResult.processDefinitionKeysByProcessId().entrySet()) {
      var processDefinitionId = importEntry.getKey();
      var importedVersionKeys = importEntry.getValue();
      var importType = importResult.importType();

      var versionsInState =
          processStates.computeIfAbsent(processDefinitionId, k -> new HashMap<>());
      var partialUpdate =
          computePartialUpdate(
              processDefinitionId, importedVersionKeys, importType, versionsInState);
      toActivate.addAll(partialUpdate.toActivate());
      toDeactivate.addAll(partialUpdate.toDeactivate());
    }

    // Now, handle processDefinitionIds present in state but missing from import
    Set<ProcessDefinitionRef> missingInImport = new HashSet<>(processStates.keySet());
    missingInImport.removeAll(importedProcessIds);

    for (var processDefinitionId : missingInImport) {
      var versionsInState = processStates.get(processDefinitionId);
      var partialUpdate =
          computePartialUpdate(
              processDefinitionId,
              Set.of(), // empty set of imported versions
              importResult.importType(),
              versionsInState);
      toActivate.addAll(partialUpdate.toActivate());
      toDeactivate.addAll(partialUpdate.toDeactivate());
      // If all versions are removed, also remove the processDefinitionId entry
      if (versionsInState.isEmpty()) {
        processStates.remove(processDefinitionId);
      }
    }
    return new StateUpdateResult(toActivate, toDeactivate);
  }

  /** Computes the state update for a single process definition ID in an import. */
  private static StateUpdateResult computePartialUpdate(
      ProcessDefinitionRef processDefinitionRef,
      Set<Long> importedVersions,
      ImportType importType,
      Map<Long, MutableProcessVersionState> versionsInState) {
    LOGGER.debug(
        "computePartialUpdate for process definition id '{}' after a {} import",
        processDefinitionRef.bpmnProcessId(),
        importType);

    Set<Long> missingInImport = rightOuterJoin(importedVersions, versionsInState.keySet());
    Set<Long> missingInState = rightOuterJoin(versionsInState.keySet(), importedVersions);
    Set<Long> presentInBoth = new HashSet<>(importedVersions);
    presentInBoth.retainAll(versionsInState.keySet());

    LOGGER.debug("The following versions are missing in the new import: {}", missingInImport);
    LOGGER.debug("The following versions are not present in state: {}", missingInState);
    LOGGER.debug("The following versions are present in both: {}", presentInBoth);

    final Set<ProcessDefinitionRefAndKey> toActivate = new HashSet<>();
    final Set<ProcessDefinitionRefAndKey> toDeactivate = new HashSet<>();

    // Handle versions that are missing in the import (mark flag as false, potentially deactivate)
    for (long key : missingInImport) {
      var versionState = versionsInState.get(key);
      markStateFlags(versionState, importType, false);
      if (versionState.isInactive()) {
        toDeactivate.add(new ProcessDefinitionRefAndKey(processDefinitionRef, key));
        versionsInState.remove(key);
      }
    }

    // Handle versions that are missing in state (create new state, mark flag as true, activate)
    for (long key : missingInState) {
      var versionState = MutableProcessVersionState.init();
      markStateFlags(versionState, importType, true);
      versionsInState.put(key, versionState);
      toActivate.add(new ProcessDefinitionRefAndKey(processDefinitionRef, key));
    }

    // Handle versions that are present in both (mark flag as true, potentially activate)
    for (Long key : presentInBoth) {
      var versionState = versionsInState.get(key);
      boolean wasInactive = versionState.isInactive();
      markStateFlags(versionState, importType, true);
      if (wasInactive && !versionState.isInactive()) {
        toActivate.add(new ProcessDefinitionRefAndKey(processDefinitionRef, key));
      }
    }

    LOGGER.debug(
        "The following versions of the process '{}' will be activated {}",
        processDefinitionRef.bpmnProcessId(),
        toActivate);
    LOGGER.debug(
        "The following versions of the process '{}' will be deactivated {}",
        processDefinitionRef.bpmnProcessId(),
        toDeactivate);
    return new StateUpdateResult(toActivate, toDeactivate);
  }

  private static Set<Long> rightOuterJoin(Set<Long> left, Set<Long> right) {
    Set<Long> result = new HashSet<>(right);
    result.removeAll(left);
    return result;
  }

  private static void markStateFlags(
      MutableProcessVersionState processState,
      ImportResult.ImportType importType,
      boolean isActive) {
    switch (importType) {
      case LATEST_VERSIONS -> processState.setLatest(isActive);
      case HAVE_ACTIVE_SUBSCRIPTIONS -> processState.setHasActiveSubscriptions(isActive);
    }
  }
}
