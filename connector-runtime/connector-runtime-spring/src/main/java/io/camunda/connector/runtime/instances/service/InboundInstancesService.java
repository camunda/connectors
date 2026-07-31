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
package io.camunda.connector.runtime.instances.service;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableQuery;
import io.camunda.connector.runtime.inbound.executable.ConnectorDataMapper;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InboundInstancesService {
  private final InboundExecutableRegistry executableRegistry;

  private final ConnectorDataMapper connectorDataMapper;

  public InboundInstancesService(InboundExecutableRegistry executableRegistry) {
    this(executableRegistry, null);
  }

  public InboundInstancesService(
      InboundExecutableRegistry executableRegistry,
      WebhookConnectorRegistry webhookConnectorRegistry) {
    this.executableRegistry = executableRegistry;
    this.connectorDataMapper =
        new ConnectorDataMapper(
            webhookConnectorRegistry != null
                && webhookConnectorRegistry.appendsPhysicalTenantAndTenantToPath());
  }

  public List<InstanceAwareModel.InstanceAwareHealth> findInstanceAwareHealth(
      String executableId, String hostname) {
    var executable = findExecutable(executableId);
    Health health = executable.health();
    return List.of(
        new InstanceAwareModel.InstanceAwareHealth(
            health.getStatus(),
            health.getError(),
            health.getDetails(),
            health.getLastUpdatedAt().atOffset(ZoneOffset.UTC),
            hostname));
  }

  public List<InstanceAwareModel.InstanceAwareActivity> findInstanceAwareActivityLogs(
      String executableId, String hostname) {
    var executable = findExecutable(executableId);
    return executable.logs().stream()
        .map(
            activity ->
                new InstanceAwareModel.InstanceAwareActivity(
                    activity.severity(),
                    activity.tag(),
                    activity.timestamp(),
                    activity.message(),
                    hostname))
        .toList();
  }

  public ActiveInboundConnectorResponse findExecutable(String executableId) {
    return findExecutable(executableId, false);
  }

  /**
   * @param appendPhysicalTenantAndTenantToPath whether the caller wants {@code inbound.context}
   *     rewritten to the physical-tenant/tenant-scoped path (still gated by whether path scoping is
   *     enabled server-side, see {@link ConnectorDataMapper}) — exposed so {@code
   *     InboundInstancesRestController}'s single-executable endpoint can opt in via a request query
   *     parameter, since not every consumer of that endpoint has adapted to the scoped path yet.
   */
  public ActiveInboundConnectorResponse findExecutable(
      String executableId, boolean appendPhysicalTenantAndTenantToPath) {
    var executables =
        getActiveInboundConnectors(
            f -> f.executableId(executableId), appendPhysicalTenantAndTenantToPath);
    if (executables.isEmpty()) {
      throw new DataNotFoundException(ActiveInboundConnectorResponse.class, executableId);
    }
    return executables.getFirst();
  }

  public ConnectorInstances findConnectorInstancesOfType(String type) {
    return findConnectorInstancesOfType(type, null);
  }

  /**
   * @param physicalTenantIds when non-empty, restricts results to connectors registered under one
   *     of these physical tenants (engines) — a distinct dimension from the logical {@code
   *     tenantId} exposed elsewhere on this API.
   */
  public ConnectorInstances findConnectorInstancesOfType(
      String type, List<String> physicalTenantIds) {
    var connectorInstances =
        getConnectorsInstances(f -> f.type(type).physicalTenantIds(physicalTenantIds));
    if (connectorInstances.isEmpty()) {
      throw new DataNotFoundException(ConnectorInstances.class, type);
    }
    return connectorInstances.getFirst();
  }

  public List<ConnectorInstances> findAllConnectorInstances() {
    return findAllConnectorInstances(null);
  }

  /**
   * @param physicalTenantIds when non-empty, restricts results to connectors registered under one
   *     of these physical tenants (engines) — a distinct dimension from the logical {@code
   *     tenantId} exposed elsewhere on this API.
   */
  public List<ConnectorInstances> findAllConnectorInstances(List<String> physicalTenantIds) {
    return getConnectorsInstances(f -> f.physicalTenantIds(physicalTenantIds));
  }

  /**
   * Resets the connector executable identified by the given executable ID. The executable is
   * deactivated and re-activated with a fresh instance while keeping its current context. Blocks
   * until the restart completes.
   *
   * @param executableId the executable ID
   * @return the {@link ActiveInboundConnectorResponse} of the newly activated executable
   * @throws RuntimeException if the restart fails
   */
  public ActiveInboundConnectorResponse resetExecutable(String executableId) {
    var executable = findExecutable(executableId);
    executableRegistry.reset(executable.executableId());
    return findExecutable(executableId);
  }

  /**
   * Be aware that this API is used by c4-connectors to get the active inbound connectors grouped by
   * connector type. Changing the response format will break the c4-connectors, so make sure to
   * update the c4-connectors as well.
   *
   * @param filter the filter to apply when querying the active executables. If null, no filter is
   *     applied and all active executables are returned.
   * @return a list of {@link ConnectorInstances} grouped by connector type
   */
  private List<ConnectorInstances> getConnectorsInstances(Consumer<ActiveExecutableQuery> filter) {
    var activeInboundConnectors = getActiveInboundConnectors(filter, false);
    return activeInboundConnectors.stream()
        .collect(Collectors.groupingBy(ActiveInboundConnectorResponse::type, Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            e ->
                new ConnectorInstances(
                    e.getKey(), executableRegistry.getConnectorName(e.getKey()), e.getValue()))
        .toList();
  }

  private List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      Consumer<ActiveExecutableQuery> filter, boolean appendPhysicalTenantAndTenantToPath) {
    return executableRegistry.query(filter).stream()
        .map(
            r ->
                connectorDataMapper.createActiveInboundConnectorResponse(
                    r, appendPhysicalTenantAndTenantToPath))
        .collect(Collectors.toList());
  }
}
