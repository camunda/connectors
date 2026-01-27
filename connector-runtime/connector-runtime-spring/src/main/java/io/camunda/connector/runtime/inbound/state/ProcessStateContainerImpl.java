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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    // Track all processes that were affected by this import (state changed)
    final Set<ProcessDefinitionRef> affectedProcesses = new HashSet<>();

    // Track all imported processDefinitionIds
    Set<ProcessDefinitionRef> importedProcessIds =
        importResult.processDefinitionKeysByProcessId().keySet();

    // First, process all imported processDefinitionIds
    for (var importEntry : importResult.processDefinitionKeysByProcessId().entrySet()) {
      var processDefinitionId = importEntry.getKey();
      var importedProcessDefinitionKeys = importEntry.getValue();
      var importType = importResult.importType();

      var versionsInState =
          processStates.computeIfAbsent(processDefinitionId, k -> new HashMap<>());
      boolean stateChanged =
          applyPartialUpdate(
              processDefinitionId, importedProcessDefinitionKeys, importType, versionsInState);
      if (stateChanged) {
        affectedProcesses.add(processDefinitionId);
      }
    }

    // Now, handle processDefinitionIds present in state but missing from import
    Set<ProcessDefinitionRef> missingInImport = new HashSet<>(processStates.keySet());
    missingInImport.removeAll(importedProcessIds);

    for (var processDefinitionId : missingInImport) {
      var processDefinitionKeysInState = processStates.get(processDefinitionId);
      boolean stateChanged =
          applyPartialUpdate(
              processDefinitionId,
              Set.of(), // empty set of imported versions
              importResult.importType(),
              processDefinitionKeysInState);
      if (stateChanged) {
        affectedProcesses.add(processDefinitionId);
      }
      // If all versions are removed, also remove the processDefinitionId entry
      if (processDefinitionKeysInState.isEmpty()) {
        processStates.remove(processDefinitionId);
      }
    }

    // Build the result: for each affected process, return all currently active versions
    Map<ProcessDefinitionRef, Set<Long>> result =
        affectedProcesses.stream()
            .collect(Collectors.toMap(processRef -> processRef, this::getActiveVersions));

    return new StateUpdateResult(result);
  }

  /** Returns the set of currently active process definition keys for a given process definition. */
  private Set<Long> getActiveVersions(ProcessDefinitionRef processRef) {
    var versionsInState = processStates.get(processRef);
    if (versionsInState == null) {
      return Set.of();
    }
    return versionsInState.entrySet().stream()
        .filter(entry -> !entry.getValue().isInactive())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Applies a partial update for a single process definition ID. Returns true if any version's
   * active state changed.
   */
  private boolean applyPartialUpdate(
      ProcessDefinitionRef processDefinitionRef,
      Set<Long> importedVersions,
      ImportType importType,
      Map<Long, MutableProcessVersionState> versionsInState) {

    Set<Long> missingInImport = rightOuterJoin(importedVersions, versionsInState.keySet());
    Set<Long> missingInState = rightOuterJoin(versionsInState.keySet(), importedVersions);
    Set<Long> presentInBoth = new HashSet<>(importedVersions);
    presentInBoth.retainAll(versionsInState.keySet());

    LOGGER.trace("Process definition '{}':", processDefinitionRef.bpmnProcessId());
    LOGGER.trace("* Missing in the import: {} ({})", missingInImport, importType);
    LOGGER.trace("* Imported as but missing in state: {} ({})", missingInState, importType);
    LOGGER.trace("* Present in both: {} ({})", presentInBoth, importType);

    boolean anyStateChanged =
        deactivateVersionsMissingInImport(missingInImport, importType, versionsInState)
            || activateVersionsMissingInState(missingInState, importType, versionsInState);

    updateVersionsPresentInBoth(presentInBoth, importType, versionsInState);

    if (anyStateChanged && LOGGER.isDebugEnabled()) {
      var activeVersions =
          versionsInState.entrySet().stream()
              .filter(e -> !e.getValue().isInactive())
              .map(Map.Entry::getKey)
              .toList();
      LOGGER.debug(
          "* Process '{}': state changed, active versions: {}",
          processDefinitionRef.bpmnProcessId(),
          activeVersions);
    }

    return anyStateChanged;
  }

  private Set<Long> rightOuterJoin(Set<Long> left, Set<Long> right) {
    Set<Long> result = new HashSet<>(right);
    result.removeAll(left);
    return result;
  }

  /**
   * Handles versions that are missing in the import (mark flag as false, potentially deactivate).
   * Returns true if any version's active state changed.
   */
  private boolean deactivateVersionsMissingInImport(
      Set<Long> missingInImport,
      ImportType importType,
      Map<Long, MutableProcessVersionState> versionsInState) {
    boolean anyStateChanged = false;
    for (long key : missingInImport) {
      var versionState = versionsInState.get(key);
      boolean wasActive = !versionState.isInactive();
      markStateFlags(versionState, importType, false);
      boolean isActive = !versionState.isInactive();
      if (wasActive != isActive) {
        anyStateChanged = true;
      }
      if (versionState.isInactive()) {
        versionsInState.remove(key);
      }
    }
    return anyStateChanged;
  }

  /**
   * Handles versions that are missing in state (create new state, mark flag as true, activate).
   * Returns true if any new version was added.
   */
  private boolean activateVersionsMissingInState(
      Set<Long> missingInState,
      ImportType importType,
      Map<Long, MutableProcessVersionState> versionsInState) {
    boolean anyStateChanged = false;
    for (long key : missingInState) {
      var versionState = MutableProcessVersionState.init();
      markStateFlags(versionState, importType, true);
      versionsInState.put(key, versionState);
      anyStateChanged = true; // new version always means state changed
    }
    return anyStateChanged;
  }

  /** Handles versions that are present in both (mark flag as true). */
  private void updateVersionsPresentInBoth(
      Set<Long> presentInBoth,
      ImportType importType,
      Map<Long, MutableProcessVersionState> versionsInState) {
    for (Long key : presentInBoth) {
      var versionState = versionsInState.get(key);
      markStateFlags(versionState, importType, true);
    }
  }

  private void markStateFlags(
      MutableProcessVersionState processState,
      ImportResult.ImportType importType,
      boolean isActive) {
    switch (importType) {
      case LATEST_VERSIONS -> processState.setLatest(isActive);
      case HAVE_ACTIVE_SUBSCRIPTIONS -> processState.setHasActiveSubscriptions(isActive);
    }
  }
}
