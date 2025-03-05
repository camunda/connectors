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
package io.camunda.connector.runtime.inbound.controller;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.inbound.executable.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

/**
 * This controller is used by the c4-connectors to get the active inbound connectors, executables
 * and logs.
 *
 * <p><b>Note:</b> Be aware that changing the response format will break the c4-connectors, so make
 * sure to update the c4-connectors as well.
 */
@RestController
@RequestMapping("/inbound-instances")
public class InboundInstancesRestController {

  private final InboundExecutableRegistry executableRegistry;

  private final ConnectorDataMapper connectorDataMapper = new ConnectorDataMapper();

  public InboundInstancesRestController(InboundExecutableRegistry executableRegistry) {
    this.executableRegistry = executableRegistry;
  }

  @GetMapping()
  public List<ConnectorInstances> getConnectorInstances() {
    return getConnectorsInstances(null);
  }

  @GetMapping("/{type}")
  public ConnectorInstances getConnectorInstance(@PathVariable(name = "type") String type) {
    var instances = getConnectorsInstances(type);
    if (instances.isEmpty()) {
      throw new DataNotFoundException(ConnectorInstances.class, type);
    }
    return instances.getFirst();
  }

  @GetMapping("/{type}/executables/{executableId}")
  public ActiveInboundConnectorResponse getConnectorInstanceExecutable(
      @PathVariable(name = "type") String type,
      @PathVariable(name = "executableId") String executableId) {
    var instances = getConnectorsInstances(type);
    if (instances.isEmpty()) {
      throw new DataNotFoundException(ConnectorInstances.class, type);
    }
    var instance = instances.getFirst();
    var executables =
        instance.instances().stream()
            .filter(
                activeInboundConnectorResponse ->
                    activeInboundConnectorResponse.executableId().toString().equals(executableId))
            .toList();
    if (executables.isEmpty()) {
      throw new DataNotFoundException(ActiveInboundConnectorResponse.class, executableId);
    }
    return executables.getFirst();
  }

  @GetMapping("/{type}/executables/{executableId}/logs")
  public Collection<Activity> getConnectorInstanceExecutableLogs(
      @PathVariable(name = "type") String type,
      @PathVariable(name = "executableId") String executableId) {
    var executable = getConnectorInstanceExecutable(type, executableId);
    var processIds =
        executable.elements().stream().map(ProcessElement::bpmnProcessId).distinct().toList();
    if (processIds.size() > 1) {
      throw new RuntimeException(
          "Multiple process ids found for the executable id: "
              + executableId
              + ". This is not supported yet.");
    }
    var processId = processIds.getFirst();
    var result =
        executableRegistry
            .query(new ActiveExecutableQuery(processId, null, type, executable.tenantId()))
            .stream()
            .filter(
                activeExecutableResponse ->
                    activeExecutableResponse.executableId().toString().equals(executableId))
            .findFirst();
    if (result.isEmpty()) {
      throw new DataNotFoundException(Activity.class, executableId);
    }
    return result.get().logs();
  }

  /**
   * Be aware that this API is used by c4-connectors to get the active inbound connectors grouped by
   * connector type. Changing the response format will break the c4-connectors, so make sure to
   * update the c4-connectors as well.
   *
   * @param type the connectorId to filter by (also called 'connectorId')
   */
  private List<ConnectorInstances> getConnectorsInstances(String type) {
    var activeInboundConnectors = getActiveInboundConnectors(type);
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

  private List<ActiveInboundConnectorResponse> getActiveInboundConnectors(String type) {
    return executableRegistry.query(new ActiveExecutableQuery(null, null, type, null)).stream()
        .map(connectorDataMapper::createActiveInboundConnectorResponse)
        .collect(Collectors.toList());
  }
}
