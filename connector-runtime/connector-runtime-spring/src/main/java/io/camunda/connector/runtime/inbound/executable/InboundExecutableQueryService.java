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

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Health.Status;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Cancelled;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.ConnectorNotRegistered;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.FailedToActivate;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.InvalidDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Service responsible for handling queries against the executable state and building responses. */
public class InboundExecutableQueryService {

  private final InboundExecutableStateStore stateStore;
  private final InboundConnectorFactory connectorFactory;
  private final ActivityLogRegistry activityLogRegistry;

  public InboundExecutableQueryService(
      InboundExecutableStateStore stateStore,
      InboundConnectorFactory connectorFactory,
      ActivityLogRegistry activityLogRegistry) {
    this.stateStore = stateStore;
    this.connectorFactory = connectorFactory;
    this.activityLogRegistry = activityLogRegistry;
  }

  /**
   * Query executables matching the given criteria.
   *
   * @param query the query parameters
   * @return list of matching executable responses
   */
  public List<ActiveExecutableResponse> query(ActiveExecutableQuery query) {
    return stateStore.getAllExecutables().stream()
        .map(this::buildActiveExecutableResponse)
        .filter(response -> matchesQuery(response, query))
        .collect(Collectors.toList());
  }

  /**
   * Get the connector name for a given connector type.
   *
   * @param type the connector type
   * @return the connector name, or null if not found
   */
  public String getConnectorName(String type) {
    try {
      return connectorFactory.getActiveConfigurations().stream()
          .filter(configuration -> configuration.type().equals(type))
          .findFirst()
          .map(configuration -> configuration.name())
          .orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  private boolean matchesQuery(ActiveExecutableResponse response, ActiveExecutableQuery query) {
    var elements = response.elements();
    if (elements.isEmpty()) {
      return false;
    }

    if (query == null) {
      return true;
    }

    var firstElement = elements.getFirst();

    if (query.type() != null && !query.type().equals(firstElement.type())) {
      return false;
    }
    if (query.tenantId() != null && !query.tenantId().equals(firstElement.tenantId())) {
      return false;
    }
    if (query.bpmnProcessId() != null
        && !query.bpmnProcessId().equals(firstElement.element().bpmnProcessId())) {
      return false;
    }
    if (query.executableId() != null && !query.executableId().equals(response.executableId())) {
      return false;
    }
    if (query.elementId() != null) {
      return elements.stream()
          .anyMatch(element -> element.element().elementId().equals(query.elementId()));
    }
    return true;
  }

  private ActiveExecutableResponse buildActiveExecutableResponse(RegisteredExecutable executable) {
    return switch (executable) {
      case Activated activated -> buildFromActivated(activated);
      case Cancelled cancelled -> buildFromCancelled(cancelled);
      case ConnectorNotRegistered notRegistered -> buildFromNotRegistered(notRegistered);
      case FailedToActivate failed -> buildFromFailedToActivate(failed);
      case InvalidDefinition invalid -> buildFromInvalidDefinition(invalid);
    };
  }

  private ActiveExecutableResponse buildFromActivated(Activated activated) {
    var context = activated.context();

    return new ActiveExecutableResponse(
        activated.id(),
        activated.executable().getClass(),
        context.connectorElements(),
        context.getHealth(),
        activityLogRegistry.getLogs(activated.id()),
        context.getActivationTimestamp());
  }

  private ActiveExecutableResponse buildFromCancelled(Cancelled cancelled) {
    var context = cancelled.context();

    return new ActiveExecutableResponse(
        cancelled.id(),
        cancelled.executable().getClass(),
        context.connectorElements(),
        context.getHealth(),
        activityLogRegistry.getLogs(cancelled.id()),
        context.getActivationTimestamp());
  }

  private ActiveExecutableResponse buildFromNotRegistered(ConnectorNotRegistered notRegistered) {
    var data = notRegistered.data();

    return new ActiveExecutableResponse(
        notRegistered.id(),
        null,
        data.connectorElements(),
        notRegistered.health(),
        activityLogRegistry.getLogs(notRegistered.id()),
        null);
  }

  private ActiveExecutableResponse buildFromFailedToActivate(FailedToActivate failed) {
    var data = failed.data();

    return new ActiveExecutableResponse(
        failed.id(),
        null,
        data.connectorElements(),
        failed.health(),
        activityLogRegistry.getLogs(failed.id()),
        null);
  }

  private ActiveExecutableResponse buildFromInvalidDefinition(InvalidDefinition invalid) {
    var data = invalid.data();

    return new ActiveExecutableResponse(
        invalid.id(),
        null,
        data.connectorElements(),
        invalid.health(),
        activityLogRegistry.getLogs(invalid.id()),
        null);
  }

  /**
   * Aggregates health status across all executables.
   *
   * @return aggregated health status
   */
  public Health aggregateHealth() {
    var executables = stateStore.getAllExecutables();

    if (executables.isEmpty()) {
      return Health.up();
    }

    Map<String, Object> downHealthsById = new LinkedHashMap<>();
    for (RegisteredExecutable executable : executables) {
      var health = getHealthFromExecutable(executable);
      if (health.getStatus() == Status.DOWN) {
        downHealthsById.put(executable.id().getId(), health);
      }
    }

    if (!downHealthsById.isEmpty()) {
      return Health.down(
          new Health.Error(
              "CONNECTORS_DOWN",
              downHealthsById.size() + " of " + executables.size() + " connectors are down"),
          downHealthsById);
    }

    return Health.up();
  }

  private Health getHealthFromExecutable(RegisteredExecutable executable) {
    return switch (executable) {
      case Activated activated -> enrich(activated.context().getHealth(), activated.context());
      case Cancelled cancelled -> enrich(cancelled.context().getHealth(), cancelled.context());
      case ConnectorNotRegistered notRegistered ->
          enrich(notRegistered.health(), notRegistered.data());
      case FailedToActivate failed -> enrich(failed.health(), failed.data());
      case InvalidDefinition invalid -> enrich(invalid.health(), invalid.data());
    };
  }

  private Health enrich(Health health, InboundConnectorManagementContext context) {
    var def = context.getDefinition();
    Map<String, Object> details = new LinkedHashMap<>();
    if (health.getDetails() != null) details.putAll(health.getDetails());
    if (!def.elements().isEmpty()) {
      details.putIfAbsent("processId", def.elements().getFirst().bpmnProcessId());
    }
    details.putIfAbsent("tenantId", def.tenantId());
    details.putIfAbsent("type", def.type());
    return health.withDetails(details);
  }

  private Health enrich(Health health, InboundConnectorDetails data) {
    Map<String, Object> details = new LinkedHashMap<>();
    if (health.getDetails() != null) details.putAll(health.getDetails());
    details.putIfAbsent("processId", data.processDefinitionId());
    details.putIfAbsent("tenantId", data.tenantId());
    details.putIfAbsent("type", data.type());
    return health.withDetails(details);
  }
}
