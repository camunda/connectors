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
import io.camunda.connector.api.inbound.Health.Error;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.Deactivated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Cancelled;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.ConnectorNotRegistered;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.InvalidDefinition;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class InboundExecutableRegistryImpl implements InboundExecutableRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(InboundExecutableRegistryImpl.class);
  private static final String ACTIVATION_FAILURE = "ACTIVATION_FAILURE";
  private static final String CONNECTOR_NOT_REGISTERED = "CONNECTOR_NOT_REGISTERED";
  final Map<ExecutableId, RegisteredExecutable> executables = new ConcurrentHashMap<>();
  private final BlockingQueue<InboundExecutableEvent> eventQueue;
  private final ExecutorService executorService;
  private final BatchExecutableProcessor batchExecutableProcessor;
  private final Map<ProcessElementWithRuntimeData, ExecutableId> executablesByElement =
      new ConcurrentHashMap<>();
  private final Map<String, List<String>> deduplicationScopesByType;
  private final Map<String, String> connectorsNamesByType;

  private final ActivityLogRegistry activityLogRegistry;

  public InboundExecutableRegistryImpl(
      InboundConnectorFactory connectorFactory,
      BatchExecutableProcessor batchExecutableProcessor,
      ActivityLogRegistry activityLogRegistry) {
    this.batchExecutableProcessor = batchExecutableProcessor;
    this.activityLogRegistry = activityLogRegistry;
    this.executorService = Executors.newSingleThreadExecutor();
    eventQueue = new LinkedBlockingQueue<>();
    connectorsNamesByType =
        connectorFactory.getConfigurations().stream()
            .collect(
                Collectors.toMap(
                    InboundConnectorConfiguration::type, InboundConnectorConfiguration::name));
    deduplicationScopesByType =
        connectorFactory.getConfigurations().stream()
            .collect(
                Collectors.toMap(
                    InboundConnectorConfiguration::type,
                    InboundConnectorConfiguration::deduplicationProperties));
    startEventProcessing();
  }

  void startEventProcessing() {
    executorService.submit(
        () -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              handleEvent(eventQueue.take()); // blocks until an event is available
            }
          } catch (InterruptedException e) {
            LOG.error("Event processing thread interrupted", e);
          }
        });
  }

  @Override
  public void publishEvent(InboundExecutableEvent event) {
    eventQueue.add(event);
    LOG.debug("Event added to the queue: {}", event);
  }

  void handleEvent(InboundExecutableEvent event) {
    switch (event) {
      case InboundExecutableEvent.Activated activated -> handleActivated(activated);
      case Deactivated deactivated -> handleDeactivated(deactivated);
      case InboundExecutableEvent.Cancelled cancelled -> handleCancelled(cancelled);
    }
  }

  private void handleCancelled(InboundExecutableEvent.Cancelled cancelled) {
    RegisteredExecutable executable = this.executables.get(cancelled.id());
    if (executable instanceof Activated activated) {
      Cancelled cancelledExecutable =
          this.batchExecutableProcessor.cancelExecutable(activated, cancelled.throwable());
      this.executables.replace(cancelled.id(), cancelledExecutable);
      if (cancelled.throwable() instanceof ConnectorRetryException retryException) {
        this.batchExecutableProcessor
            .restartFromContext(cancelledExecutable, retryException)
            .thenAccept(
                registeredExecutable ->
                    this.executables.replace(cancelled.id(), registeredExecutable))
            .exceptionally(
                throwable -> {
                  LOG.error("The inbound connector could not be restarted", throwable);
                  return null;
                });
      }
    } else {
      LOG.error(
          "Attempted to cancel an inbound connector executable that is not in the active state");
    }
  }

  private void handleActivated(InboundExecutableEvent.Activated activated) {
    LOG.debug(
        "Handling activated event for process definition {} (tenant {})",
        activated.processDefinitionKey(),
        activated.tenantId());

    var elements = activated.elements();
    if (elements.isEmpty()) {
      LOG.debug("No elements provided for activation");
      return;
    }

    var processId = activated.tenantId() + activated.processDefinitionKey();

    synchronized (processId.intern()) {
      try {
        Map<ExecutableId, InboundConnectorDetails> groupedConnectors =
            groupElements(elements).stream()
                .collect(Collectors.toMap(InboundConnectorDetails::id, connector -> connector));

        groupedConnectors.forEach(
            (id, connectorDetails) ->
                connectorDetails
                    .connectorElements()
                    .forEach(element -> executablesByElement.put(element.element(), id)));

        var activationResult =
            batchExecutableProcessor.activateBatch(groupedConnectors, this::createCancellation);
        executables.putAll(activationResult);

      } catch (Exception e) {
        LOG.error("Failed to activate connectors", e);
      }
    }
  }

  public void createCancellation(InboundExecutableEvent.Cancelled cancelEvent) {
    RegisteredExecutable executable = this.executables.get(cancelEvent.id());
    if (executable instanceof Activated) {
      this.publishEvent(cancelEvent);
    } else throw new IllegalStateException();
  }

  private void handleDeactivated(Deactivated deactivated) {
    LOG.debug(
        "Handling deactivated event for process {} (tenant {}) ",
        deactivated.processDefinitionKey(),
        deactivated.tenantId());

    var processId = deactivated.tenantId() + deactivated.processDefinitionKey();

    synchronized (processId.intern()) {
      try {
        var executablesToDeactivate =
            executablesByElement.keySet().stream()
                .filter(
                    element ->
                        element.tenantId().equals(deactivated.tenantId())
                            && element.processDefinitionKey() == deactivated.processDefinitionKey())
                .map(executablesByElement::remove)
                .map(executables::remove)
                .toList();

        if (executablesToDeactivate.isEmpty()) {
          LOG.debug("No executables found for deactivation");
          return;
        }

        batchExecutableProcessor.deactivateBatch(executablesToDeactivate);

      } catch (Exception e) {
        LOG.error("Failed to deactivate connectors", e);
      }
    }
  }

  @Override
  public List<ActiveExecutableResponse> query(ActiveExecutableQuery query) {
    return executables.entrySet().stream()
        .filter(entry -> matchesQuery(entry.getValue(), query))
        .map(entry -> mapToResponse(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<InboundConnectorDetails> groupElements(List<InboundConnectorElement> elements) {

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

  private boolean matchesQuery(RegisteredExecutable executable, ActiveExecutableQuery query) {
    List<ProcessElementWithRuntimeData> elements =
        switch (executable) {
          case Activated activated ->
              activated.context().connectorElements().stream()
                  .map(InboundConnectorElement::element)
                  .toList();
          case FailedToActivate failed ->
              failed.data().connectorElements().stream()
                  .map(InboundConnectorElement::element)
                  .toList();
          case ConnectorNotRegistered notRegistered ->
              notRegistered.data().connectorElements().stream()
                  .map(InboundConnectorElement::element)
                  .toList();
          case InvalidDefinition invalid ->
              invalid.data().connectorElements().stream()
                  .map(InboundConnectorElement::element)
                  .toList();
          case Cancelled cancelled ->
              cancelled.context().connectorElements().stream()
                  .map(InboundConnectorElement::element)
                  .toList();
        };
    var type =
        switch (executable) {
          case Activated activated -> activated.context().getDefinition().type();
          case FailedToActivate failed -> failed.data().connectorElements().getFirst().type();
          case ConnectorNotRegistered notRegistered -> notRegistered.data().type();
          case InvalidDefinition invalid -> invalid.data().connectorElements().getFirst().type();
          case Cancelled cancelled -> cancelled.context().getDefinition().type();
        };

    return elements.stream()
        .anyMatch(
            element ->
                processIdMatches(element, query)
                    && typeMatches(type, query)
                    && tenantIdMatches(element, query)
                    && elementIdMatches(element.elementId(), query));
  }

  private boolean processIdMatches(
      ProcessElementWithRuntimeData element, ActiveExecutableQuery query) {
    return query.bpmnProcessId() == null || query.bpmnProcessId().equals(element.bpmnProcessId());
  }

  private boolean tenantIdMatches(
      ProcessElementWithRuntimeData element, ActiveExecutableQuery query) {
    return query.tenantId() == null || query.tenantId().equals(element.tenantId());
  }

  private boolean typeMatches(String type, ActiveExecutableQuery query) {
    return query.type() == null || type == null || query.type().equals(type);
  }

  private boolean elementIdMatches(String elementId, ActiveExecutableQuery query) {
    return query.elementId() == null || query.elementId().equals(elementId);
  }

  private ActiveExecutableResponse mapToResponse(ExecutableId id, RegisteredExecutable connector) {

    return switch (connector) {
      case Activated activated ->
          new ActiveExecutableResponse(
              id,
              activated.executable().getClass(),
              activated.context().connectorElements(),
              activated.context().getHealth(),
              activityLogRegistry.getLogs(id),
              activated.context().getActivationTimestamp());
      case FailedToActivate failed ->
          new ActiveExecutableResponse(
              id,
              null,
              failed.data().connectorElements(),
              Health.down(new Error(ACTIVATION_FAILURE, failed.reason())),
              List.of(),
              null);
      case ConnectorNotRegistered notRegistered ->
          new ActiveExecutableResponse(
              id,
              null,
              notRegistered.data().connectorElements(),
              Health.down(
                  new Error(
                      CONNECTOR_NOT_REGISTERED,
                      "Connector " + notRegistered.data().type() + " not registered")),
              List.of(),
              null);
      case InvalidDefinition invalid ->
          new ActiveExecutableResponse(
              id,
              null,
              invalid.data().connectorElements(),
              Health.down(
                  new Error(
                      ACTIVATION_FAILURE, "Invalid connector definition: " + invalid.reason())),
              List.of(),
              null);
      case Cancelled cancelled ->
          new ActiveExecutableResponse(
              id,
              cancelled.executable().getClass(),
              cancelled.context().connectorElements(),
              Health.down(cancelled.exceptionThrown()),
              activityLogRegistry.getLogs(id),
              null);
    };
  }

  // print status report every hour
  @Scheduled(fixedRate = 60 * 60 * 1000)
  public void logStatusReport() {
    LOG.info("Inbound connector status report - {} executables active", executables.size());
    executables.values().stream()
        .collect(
            Collectors.groupingBy(
                activeExecutable ->
                    switch (activeExecutable) {
                      case Activated activated -> activated.context().getDefinition().type();
                      case FailedToActivate failed ->
                          failed.data().connectorElements().getFirst().type();
                      case ConnectorNotRegistered notRegistered -> notRegistered.data().type();
                      case InvalidDefinition invalid -> invalid.data().type();
                      case Cancelled cancelled -> cancelled.context().getDefinition().type();
                    },
                Collectors.toList()))
        .forEach(
            (type, list) -> {
              var successfullyActivatedCount =
                  list.stream().filter(Activated.class::isInstance).count();
              LOG.info(
                  ". '{}' - {}, of which {} successfully activated",
                  type,
                  list.size(),
                  successfullyActivatedCount);
              var groupedByTenant =
                  list.stream()
                      .collect(
                          Collectors.groupingBy(
                              activeExecutable ->
                                  switch (activeExecutable) {
                                    case Activated activated ->
                                        activated.context().getDefinition().tenantId();
                                    case FailedToActivate failed -> failed.data().tenantId();
                                    case ConnectorNotRegistered notRegistered ->
                                        notRegistered.data().tenantId();
                                    case InvalidDefinition invalid -> invalid.data().tenantId();
                                    case Cancelled cancelled ->
                                        cancelled.context().getDefinition().tenantId();
                                  },
                              Collectors.counting()));
              groupedByTenant.forEach(
                  (tenant, count) -> LOG.info(". . {} for tenant {}", count, tenant));
            });
  }

  @Override
  public String getConnectorName(String type) {
    return connectorsNamesByType.get(type);
  }
}
