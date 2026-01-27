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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for computing state transitions for inbound executables. Determines what
 * actions need to be taken when process state changes.
 */
public class InboundExecutableStateTransitionService {

  private static final Logger LOG =
      LoggerFactory.getLogger(InboundExecutableStateTransitionService.class);

  private final Map<String, List<String>> deduplicationScopesByType;
  private final InboundExecutableStateStore stateStore;

  public InboundExecutableStateTransitionService(
      Map<String, List<String>> deduplicationScopesByType, InboundExecutableStateStore stateStore) {
    this.deduplicationScopesByType = deduplicationScopesByType;
    this.stateStore = stateStore;
  }

  /**
   * Computes the target state from a process state change event.
   *
   * @param elements all connector elements from all active versions
   * @return the target state with valid and invalid connector details
   */
  public TargetState computeTargetState(List<InboundConnectorElement> elements) {
    List<InboundConnectorDetails> groupedConnectors = groupElementsByDeduplicationId(elements);

    Map<ExecutableId, InboundConnectorDetails.ValidInboundConnectorDetails> valid = new HashMap<>();
    Map<ExecutableId, InboundConnectorDetails.InvalidInboundConnectorDetails> invalid =
        new HashMap<>();

    for (InboundConnectorDetails details : groupedConnectors) {
      switch (details) {
        case InboundConnectorDetails.ValidInboundConnectorDetails v -> valid.put(details.id(), v);
        case InboundConnectorDetails.InvalidInboundConnectorDetails i ->
            invalid.put(details.id(), i);
      }
    }

    return new TargetState(valid, invalid);
  }

  /**
   * Computes the current state for a process.
   *
   * @param bpmnProcessId the BPMN process ID
   * @param tenantId the tenant ID
   * @return the current state
   */
  public CurrentState computeCurrentState(String bpmnProcessId, String tenantId) {
    Set<ExecutableId> executableIds =
        stateStore.getExecutableIdsForProcess(bpmnProcessId, tenantId);
    return new CurrentState(executableIds);
  }

  /**
   * Determines what actions need to be taken to transition from current to target state.
   *
   * @param target the desired target state
   * @param current the current state
   * @return the actions to execute
   */
  public StateTransitionPlan determineActions(TargetState target, CurrentState current) {
    List<PlannedAction> actions = new ArrayList<>();

    // New executables to activate
    Set<ExecutableId> toActivate = new HashSet<>(target.allIds());
    toActivate.removeAll(current.executableIds());
    for (ExecutableId id : toActivate) {
      actions.add(new PlannedAction(id, ActionType.ACTIVATE));
    }

    // Executables to deactivate (no longer in target)
    Set<ExecutableId> toDeactivate = new HashSet<>(current.executableIds());
    toDeactivate.removeAll(target.allIds());
    for (ExecutableId id : toDeactivate) {
      actions.add(new PlannedAction(id, ActionType.DEACTIVATE));
    }

    // Executables that exist in both - categorize update action
    Set<ExecutableId> potentialUpdates = new HashSet<>(current.executableIds());
    potentialUpdates.retainAll(target.allIds());

    for (ExecutableId id : potentialUpdates) {
      var updateAction = categorizeUpdate(id, target);
      if (updateAction != ActionType.NO_ACTION) {
        actions.add(new PlannedAction(id, updateAction));
      }
    }

    LOG.debug(
        "Transition plan: {} action(s) - {}",
        actions.size(),
        actions.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    PlannedAction::action, java.util.stream.Collectors.counting())));

    return new StateTransitionPlan(actions);
  }

  private ActionType categorizeUpdate(ExecutableId id, TargetState target) {
    RegisteredExecutable existing = stateStore.get(id);

    if (!(existing instanceof Activated activated)) {
      return ActionType.RESTART;
    }

    // Cross-version property mismatch - replace with invalid executable
    // User can fix this by deploying a new version with correct configuration
    if (target.invalid().containsKey(id)) {
      var invalid = target.invalid().get(id);
      LOG.warn(
          "Cross-version deduplication conflict for executable '{}': {}. "
              + "Replacing with invalid executable. Deploy a new version to fix.",
          id,
          invalid.error().getMessage());
      return ActionType.REPLACE_WITH_INVALID;
    }

    // Check compatibility with existing context
    var newDetails = target.valid().get(id);
    var existingDetails = getValidDetailsFromContext(activated.context());

    if (existingDetails == null) {
      LOG.debug("Executable '{}' needs restart: could not extract existing details", id);
      return ActionType.RESTART;
    }

    if (!areDetailsCompatibleForUpdate(existingDetails, newDetails)) {
      LOG.debug("Executable '{}' needs restart due to property changes", id);
      return ActionType.RESTART;
    }

    // Check if elements are identical - no action needed
    if (areElementsIdentical(existingDetails.connectorElements(), newDetails.connectorElements())) {
      return ActionType.NO_ACTION;
    }

    return ActionType.HOT_SWAP;
  }

  private boolean areElementsIdentical(
      List<InboundConnectorElement> existing, List<InboundConnectorElement> target) {
    if (existing.size() != target.size()) {
      return false;
    }
    // Compare by process definition keys - if the set of keys is the same, elements are identical
    var existingKeys =
        existing.stream()
            .map(e -> e.element().processDefinitionKey())
            .collect(java.util.stream.Collectors.toSet());
    var targetKeys =
        target.stream()
            .map(e -> e.element().processDefinitionKey())
            .collect(java.util.stream.Collectors.toSet());
    return existingKeys.equals(targetKeys);
  }

  private InboundConnectorDetails.ValidInboundConnectorDetails getValidDetailsFromContext(
      InboundConnectorManagementContext context) {
    var elements = context.connectorElements();
    if (elements.isEmpty()) {
      return null;
    }
    var firstElement = elements.getFirst();
    var deduplicationProperties =
        Optional.ofNullable(deduplicationScopesByType.get(firstElement.type())).orElse(List.of());
    try {
      var deduplicationId = firstElement.deduplicationId(deduplicationProperties);
      var details = InboundConnectorDetails.of(deduplicationId, elements);
      if (details instanceof InboundConnectorDetails.ValidInboundConnectorDetails valid) {
        return valid;
      }
    } catch (Exception e) {
      LOG.warn("Failed to extract details from existing context", e);
    }
    return null;
  }

  private boolean areDetailsCompatibleForUpdate(
      InboundConnectorDetails.ValidInboundConnectorDetails existing,
      InboundConnectorDetails.ValidInboundConnectorDetails newDetails) {
    if (!existing.type().equals(newDetails.type())) {
      return false;
    }
    if (!existing.tenantId().equals(newDetails.tenantId())) {
      return false;
    }
    if (!existing.deduplicationId().equals(newDetails.deduplicationId())) {
      return false;
    }
    return existing
        .rawPropertiesWithoutKeywords()
        .equals(newDetails.rawPropertiesWithoutKeywords());
  }

  private List<InboundConnectorDetails> groupElementsByDeduplicationId(List<InboundConnectorElement> elements) {
    Map<String, List<InboundConnectorElement>> groupedElements = new HashMap<>();

    for (InboundConnectorElement element : elements) {
      try {
        var deduplicationProperties =
            Optional.ofNullable(deduplicationScopesByType.get(element.type())).orElse(List.of());
        var deduplicationId = element.deduplicationId(deduplicationProperties);
        groupedElements.computeIfAbsent(deduplicationId, k -> new ArrayList<>()).add(element);
      } catch (Exception e) {
        LOG.error(
            "Failed to get deduplication ID for element {} in process {}",
            element.element().elementId(),
            element.element().bpmnProcessId(),
            e);
      }
    }

    return groupedElements.entrySet().stream()
        .map(entry -> InboundConnectorDetails.of(entry.getKey(), entry.getValue()))
        .toList();
  }

  public record TargetState(
      Map<ExecutableId, InboundConnectorDetails.ValidInboundConnectorDetails> valid,
      Map<ExecutableId, InboundConnectorDetails.InvalidInboundConnectorDetails> invalid) {

    public Set<ExecutableId> allIds() {
      Set<ExecutableId> all = new HashSet<>();
      all.addAll(valid.keySet());
      all.addAll(invalid.keySet());
      return all;
    }

    public InboundConnectorDetails get(ExecutableId id) {
      return valid.containsKey(id) ? valid.get(id) : invalid.get(id);
    }
  }

  public record CurrentState(Set<ExecutableId> executableIds) {}

  /** A single action to be taken on an executable. */
  public record PlannedAction(ExecutableId executableId, ActionType action) {}

  /** A plan of actions to transition from current to target state. */
  public record StateTransitionPlan(List<PlannedAction> actions) {

    /** Get all actions of a specific type. */
    public List<ExecutableId> getExecutableIds(ActionType type) {
      return actions.stream()
          .filter(a -> a.action() == type)
          .map(PlannedAction::executableId)
          .toList();
    }

    /** Check if the plan is empty (no actions needed). */
    public boolean isEmpty() {
      return actions.isEmpty();
    }
  }

  /** Types of actions that can be taken on an executable. */
  public enum ActionType {
    /** No changes needed - elements are identical */
    NO_ACTION,
    /** New executable - needs to be activated */
    ACTIVATE,
    /** Executable no longer needed - deactivate it */
    DEACTIVATE,
    /** Elements changed but properties compatible - update elements in-place */
    HOT_SWAP,
    /**
     * Cross-version deduplication conflict detected. The new version has incompatible properties
     * with the same deduplication ID. The existing executable will be replaced with an invalid one.
     * User can fix this by deploying a new version with correct configuration.
     */
    REPLACE_WITH_INVALID,
    /** Executable is in bad state or properties changed - kill and restart */
    RESTART
  }
}
