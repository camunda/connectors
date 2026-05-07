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
package io.camunda.connector.runtime.inbound.executable.lifecycle;

import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.BatchExecutableProcessor;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.ProcessStateChanged;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableNotFoundException;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateStore;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.ActionType;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.PlannedAction;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.StateTransitionPlan;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.TargetState;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronous lifecycle pipeline. Always invoked from a single per-{@link ProcessKey} lane thread,
 * so this class holds no locks and never returns futures. Connectors own their own internal
 * recovery; the runtime owns activation, deactivation, and operator-driven reset.
 *
 * <p>Concurrency invariant: every mutation of {@link InboundExecutableStateStore} for a {@code
 * (tenantId, bpmnProcessId)} happens on that key's lane thread.
 */
public class LifecycleExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(LifecycleExecutor.class);

  private final InboundExecutableStateStore stateStore;
  private final InboundExecutableStateTransitionService stateTransitionService;
  private final BatchExecutableProcessor batchExecutableProcessor;

  public LifecycleExecutor(
      InboundExecutableStateStore stateStore,
      InboundExecutableStateTransitionService stateTransitionService,
      BatchExecutableProcessor batchExecutableProcessor) {
    this.stateStore = stateStore;
    this.stateTransitionService = stateTransitionService;
    this.batchExecutableProcessor = batchExecutableProcessor;
  }

  /** Apply a {@link ProcessStateChanged} event: compute plan, execute it. */
  public void applyProcessStateChange(ProcessStateChanged event) {
    LOG.debug(
        "Handling state change for process '{}' (tenant '{}') with {} active version(s)",
        event.bpmnProcessId(),
        event.tenantId(),
        event.elementsByProcessDefinitionKey().size());

    try {
      List<InboundConnectorElement> allElements =
          event.elementsByProcessDefinitionKey().values().stream().flatMap(List::stream).toList();

      var targetState = stateTransitionService.computeTargetState(allElements);
      var currentState =
          stateTransitionService.computeCurrentState(event.bpmnProcessId(), event.tenantId());
      var plan = stateTransitionService.determineActions(targetState, currentState);

      if (!plan.isEmpty()) {
        executeStateTransition(plan, targetState);
      }
    } catch (Exception e) {
      LOG.error("Failed to handle state change for process '{}'", event.bpmnProcessId(), e);
    }
  }

  /**
   * Reset an executable: re-run the standard activation pipeline. Resettable from {@code Activated}
   * (currently running) and {@code FailedToActivate} (broke at startup or runtime).
   */
  public RegisteredExecutable reset(ExecutableId id) {
    var current = stateStore.get(id);
    if (current == null) {
      throw new InboundExecutableNotFoundException(id);
    }
    if (!(current instanceof Activated) && !(current instanceof FailedToActivate)) {
      throw new IllegalStateException(
          "Cannot reset executable '"
              + id
              + "': must be in Activated or FailedToActivate state, but was: "
              + current.getClass().getSimpleName());
    }

    var elements = extractElements(current);
    var targetState = stateTransitionService.computeTargetState(elements);
    executeStateTransition(
        new StateTransitionPlan(List.of(new PlannedAction(id, ActionType.RESTART))), targetState);

    var result = stateStore.get(id);
    LOG.info(
        "Connector executable '{}' reset, new state: {}",
        id,
        result == null ? "absent" : result.getClass().getSimpleName());
    return result;
  }

  private void executeStateTransition(StateTransitionPlan plan, TargetState target) {
    // 1. Deactivate executables no longer needed
    deactivateExecutables(plan.getExecutableIds(ActionType.DEACTIVATE));

    // 2. Restart executables (deactivate + activate)
    var toRestart = plan.getExecutableIds(ActionType.RESTART);
    deactivateExecutables(toRestart);

    // 3. Replace executables with invalid ones due to cross-version conflicts
    replaceWithInvalidExecutables(plan.getExecutableIds(ActionType.REPLACE_WITH_INVALID), target);

    // 4. Hot-swap elements for compatible executables
    processHotSwaps(plan.getExecutableIds(ActionType.HOT_SWAP), target);

    // 5. Activate new executables + restarted ones
    Set<ExecutableId> toActivate = new HashSet<>(plan.getExecutableIds(ActionType.ACTIVATE));
    toActivate.addAll(toRestart);
    activateExecutables(toActivate, target);
  }

  private void deactivateExecutables(List<ExecutableId> toDeactivate) {
    if (toDeactivate.isEmpty()) {
      return;
    }
    var executablesToDeactivate =
        toDeactivate.stream().map(stateStore::remove).filter(Objects::nonNull).toList();
    batchExecutableProcessor.deactivateBatch(executablesToDeactivate);
  }

  /**
   * Process hot-swaps (in-place element updates). If a hot-swap fails, the executable remains in
   * its current state with the old elements.
   */
  private void processHotSwaps(List<ExecutableId> hotSwaps, TargetState target) {
    for (ExecutableId id : hotSwaps) {
      var activated = (Activated) stateStore.get(id);
      var newDetails = target.valid().get(id);
      try {
        updateExecutableContext(activated, newDetails);
        LOG.debug(
            "Hot-swapped executable '{}' with elements from {} version(s)",
            id,
            countVersions(newDetails.connectorElements()));
      } catch (Exception e) {
        LOG.error("Failed to hot-swap executable '{}'.", id, e);
      }
    }
  }

  /**
   * Replaces existing executables with invalid ones due to cross-version configuration conflicts.
   */
  private void replaceWithInvalidExecutables(List<ExecutableId> invalidations, TargetState target) {
    for (ExecutableId id : invalidations) {
      var existingExecutable = stateStore.remove(id);
      if (existingExecutable instanceof Activated activated) {
        batchExecutableProcessor.deactivateBatch(List.of(activated));
      }
      var invalid = target.invalid().get(id);
      stateStore.put(
          id,
          new RegisteredExecutable.InvalidDefinition(invalid, invalid.error().getMessage(), id));
    }
  }

  private void activateExecutables(Set<ExecutableId> toActivate, TargetState target) {
    if (toActivate.isEmpty()) {
      return;
    }
    Map<ExecutableId, InboundConnectorDetails> connectorsToActivate = new HashMap<>();
    for (ExecutableId id : toActivate) {
      InboundConnectorDetails details = target.get(id);
      if (details != null) {
        connectorsToActivate.put(id, details);
      }
    }
    var activationResult = batchExecutableProcessor.activateBatch(connectorsToActivate);
    stateStore.putAll(activationResult);
  }

  private void updateExecutableContext(
      Activated activated, InboundConnectorDetails.ValidInboundConnectorDetails newDetails) {
    var context = activated.context();
    if (context instanceof InboundConnectorManagementContext managementContext) {
      managementContext.updateConnectorDetails(newDetails);
    } else {
      throw new IllegalStateException(
          "Cannot update context: not an InboundConnectorManagementContext");
    }
  }

  private int countVersions(List<InboundConnectorElement> elements) {
    return (int) elements.stream().map(e -> e.element().processDefinitionKey()).distinct().count();
  }

  private List<InboundConnectorElement> extractElements(RegisteredExecutable executable) {
    return switch (executable) {
      case Activated a -> a.context().connectorElements();
      case FailedToActivate f -> f.data().connectorElements();
      default ->
          throw new IllegalArgumentException(
              "Cannot extract elements from: " + executable.getClass().getSimpleName());
    };
  }
}
