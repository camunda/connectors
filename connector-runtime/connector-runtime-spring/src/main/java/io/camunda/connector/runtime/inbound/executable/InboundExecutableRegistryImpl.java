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

import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.ActionType;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.StateTransitionPlan;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableStateTransitionService.TargetState;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Cancelled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Registry for inbound executables. Orchestrates event handling, state transitions, and queries.
 */
public class InboundExecutableRegistryImpl implements InboundExecutableRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(InboundExecutableRegistryImpl.class);

  private final InboundExecutableStateStore stateStore;
  private final InboundExecutableStateTransitionService stateTransitionService;
  private final InboundExecutableQueryService queryService;
  private final BatchExecutableProcessor batchExecutableProcessor;

  private final BlockingQueue<InboundExecutableEvent> eventQueue = new LinkedBlockingQueue<>();

  public InboundExecutableRegistryImpl(
      InboundConnectorFactory connectorFactory, BatchExecutableProcessor batchExecutableProcessor) {

    this.stateStore = new InMemoryInboundExecutableStateStore();
    var deduplicationScopesByType =
        connectorFactory.getConfigurations().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    config -> config.type(),
                    config -> config.deduplicationProperties(),
                    (a, b) -> a));
    this.stateTransitionService =
        new InboundExecutableStateTransitionService(deduplicationScopesByType, stateStore);
    this.queryService = new InboundExecutableQueryService(stateStore, connectorFactory);
    this.batchExecutableProcessor = batchExecutableProcessor;
  }

  // Constructor for testing with injected dependencies
  InboundExecutableRegistryImpl(
      InboundExecutableStateStore stateStore,
      InboundExecutableStateTransitionService stateTransitionService,
      InboundExecutableQueryService queryService,
      BatchExecutableProcessor batchExecutableProcessor) {
    this.stateStore = stateStore;
    this.stateTransitionService = stateTransitionService;
    this.queryService = queryService;
    this.batchExecutableProcessor = batchExecutableProcessor;
  }

  @Override
  public void publishEvent(InboundExecutableEvent event) {
    eventQueue.add(event);
    LOG.debug("Event added to the queue: {}", event);
  }

  @Scheduled(fixedDelay = 1000)
  void processEventQueue() {
    while (!eventQueue.isEmpty()) {
      var event = eventQueue.poll();
      if (event != null) {
        handleEvent(event);
      }
    }
  }

  void handleEvent(InboundExecutableEvent event) {
    switch (event) {
      case InboundExecutableEvent.ProcessStateChanged stateChanged ->
          handleProcessStateChanged(stateChanged);
      case InboundExecutableEvent.Cancelled cancelled -> handleCancelled(cancelled);
    }
  }

  private void handleProcessStateChanged(InboundExecutableEvent.ProcessStateChanged event) {
    LOG.debug(
        "Handling state change for process '{}' (tenant '{}') with {} active version(s)",
        event.bpmnProcessId(),
        event.tenantId(),
        event.elementsByProcessDefinitionKey().size());

    var processId = event.tenantId() + event.bpmnProcessId();

    synchronized (processId.intern()) {
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
  }

  private void executeStateTransition(StateTransitionPlan plan, TargetState target) {
    // Process actions in order: deactivate first, then updates, then activate
    // This ensures we free resources before allocating new ones

    // 1. Deactivate executables no longer needed
    deactivateExecutables(plan.getExecutableIds(ActionType.DEACTIVATE));

    // 2. Restart executables (deactivate + activate)
    var toRestart = plan.getExecutableIds(ActionType.RESTART);
    deactivateExecutables(toRestart);

    // 3. Invalidate executables with cross-version conflicts
    processInvalidations(plan.getExecutableIds(ActionType.INVALIDATE), target);

    // 4. Hot-swap elements for compatible executables
    var failedHotSwaps = processHotSwaps(plan.getExecutableIds(ActionType.HOT_SWAP), target);

    // 5. Activate new executables + restarted ones + failed hot-swaps
    var toActivate = new java.util.HashSet<>(plan.getExecutableIds(ActionType.ACTIVATE));
    toActivate.addAll(toRestart);
    toActivate.addAll(failedHotSwaps);
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
   * Process hot-swaps (in-place element updates).
   *
   * @return list of executable IDs that failed hot-swap and need restart
   */
  private List<ExecutableId> processHotSwaps(List<ExecutableId> hotSwaps, TargetState target) {
    List<ExecutableId> failedHotSwaps = new java.util.ArrayList<>();

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
        LOG.error("Failed to hot-swap executable '{}', will restart", id, e);
        batchExecutableProcessor.deactivateBatch(List.of(activated));
        stateStore.remove(id);
        failedHotSwaps.add(id);
      }
    }

    return failedHotSwaps;
  }

  private void processInvalidations(List<ExecutableId> invalidations, TargetState target) {
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

  private void activateExecutables(java.util.Set<ExecutableId> toActivate, TargetState target) {
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
    var activationResult =
        batchExecutableProcessor.activateBatch(connectorsToActivate, this::createCancellation);
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

  public void createCancellation(InboundExecutableEvent.Cancelled cancelEvent) {
    RegisteredExecutable executable = stateStore.get(cancelEvent.id());
    if (executable instanceof Activated) {
      this.publishEvent(cancelEvent);
    } else {
      throw new IllegalStateException("Cannot cancel executable that is not activated");
    }
  }

  private void handleCancelled(InboundExecutableEvent.Cancelled cancelled) {
    RegisteredExecutable executable = stateStore.get(cancelled.id());
    if (executable instanceof Activated activated) {
      Cancelled cancelledExecutable =
          batchExecutableProcessor.cancelExecutable(activated, cancelled.throwable());
      stateStore.replace(cancelled.id(), cancelledExecutable);

      if (cancelled.throwable() instanceof ConnectorRetryException retryException) {
        scheduleRetry(cancelled.id(), cancelledExecutable, retryException);
      }
    } else {
      LOG.error(
          "Attempted to cancel an inbound connector executable that is not in the active state");
    }
  }

  private void scheduleRetry(
      ExecutableId id, Cancelled cancelledExecutable, ConnectorRetryException retryException) {
    batchExecutableProcessor
        .restartFromContext(cancelledExecutable, retryException)
        .thenAccept(
            activated -> {
              stateStore.replace(id, activated);
              LOG.info("Connector restarted successfully");
            })
        .exceptionally(
            throwable -> {
              LOG.error("The inbound connector could not be restarted", throwable);
              return null;
            });
  }

  @Override
  public List<ActiveExecutableResponse> query(ActiveExecutableQuery query) {
    return queryService.query(query);
  }

  @Override
  public String getConnectorName(String type) {
    return queryService.getConnectorName(type);
  }

  @Scheduled(fixedDelay = 30000)
  void logHealthStatus() {
    var health = queryService.aggregateHealth();
    if (health.getStatus() == Health.Status.DOWN) {
      LOG.warn("Inbound connector health: {}", health);
    } else {
      LOG.debug("Inbound connector health: {}", health);
    }
  }
}
