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
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.executable.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundConnectorRestController {

  private static final Logger LOG = LoggerFactory.getLogger(InboundConnectorRestController.class);

  private final InboundExecutableRegistry executableRegistry;

  public InboundConnectorRestController(InboundExecutableRegistry executableRegistry) {
    this.executableRegistry = executableRegistry;
  }

  @GetMapping("/inbound")
  public List<?> getActiveInboundConnectors(
      @RequestParam(required = false, value = "bpmnProcessId") String bpmnProcessId,
      @RequestParam(required = false, value = "elementId") String elementId,
      @RequestParam(required = false, value = "type") String type,
      @RequestParam(value = "groupedByConnectorId", defaultValue = "false")
          boolean groupedByConnectorId) {
    if (groupedByConnectorId) {
      return getConnectorsInstances(bpmnProcessId, elementId, type);
    } else {
      return getActiveInboundConnectors(bpmnProcessId, elementId, type, null);
    }
  }

  @PostMapping("/inbound/logs")
  public void logActiveInboundConnectors() {
    ((InboundExecutableRegistryImpl) executableRegistry).logStatusReport();
  }

  @GetMapping("/tenants/{tenantId}/inbound")
  public List<ActiveInboundConnectorResponse> getActiveInboundConnectorsForTenantId(
      @PathVariable(value = "tenantId") String tenantId,
      @RequestParam(required = false, value = "bpmnProcessId") String bpmnProcessId,
      @RequestParam(required = false, value = "elementId") String elementId,
      @RequestParam(required = false, value = "type") String type) {
    return getActiveInboundConnectors(bpmnProcessId, elementId, type, tenantId);
  }

  @GetMapping("/tenants/{tenantId}/inbound/{bpmnProcessId}/{elementId}/logs")
  public List<Collection<Activity>> getActiveInboundConnectorLogs(
      @PathVariable(value = "tenantId") String tenantId,
      @PathVariable(value = "bpmnProcessId") String bpmnProcessId,
      @PathVariable(value = "elementId") String elementId) {
    var result =
        executableRegistry.query(
            new ActiveExecutableQuery(bpmnProcessId, elementId, null, tenantId));
    return result.stream().map(ActiveExecutableResponse::logs).collect(Collectors.toList());
  }

  private List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      String bpmnProcessId, String elementId, String type, String tenantId) {
    return executableRegistry
        .query(new ActiveExecutableQuery(bpmnProcessId, elementId, type, tenantId))
        .stream()
        .map(this::mapToInboundResponse)
        .collect(Collectors.toList());
  }

  /**
   * Be aware that this API is used by c4-connectors to get the active inbound connectors grouped by
   * connector connectorId. Changing the response format will break the c4-connectors, so make sure
   * to update the c4-connectors as well.
   */
  private List<ConnectorInstances> getConnectorsInstances(
      String bpmnProcessId, String elementId, String type) {
    var activeInboundConnectors = getActiveInboundConnectors(bpmnProcessId, elementId, type, null);
    return activeInboundConnectors.stream()
        .collect(Collectors.groupingBy(ActiveInboundConnectorResponse::type, Collectors.toList()))
        .entrySet()
        .stream()
        .map(e -> new ConnectorInstances(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  private Map<String, String> getData(ActiveExecutableResponse connector) {
    Map<String, String> data =
        new HashMap<>(connector.elements().getFirst().connectorLevelProperties());
    var executableClass = connector.executableClass();

    if (executableClass != null
        && WebhookConnectorExecutable.class.isAssignableFrom(executableClass)) {
      try {
        var properties = connector.elements().getFirst().connectorLevelProperties();
        var contextPath = properties.get("inbound.context");
        data.put("path", contextPath);
      } catch (Exception e) {
        LOG.error("ERROR: webhook connector doesn't have context path property", e);
      }
    }
    return data;
  }

  private ActiveInboundConnectorResponse mapToInboundResponse(ActiveExecutableResponse connector) {
    var elements = connector.elements();
    var type = elements.getFirst().type();
    var tenantId = elements.getFirst().element().tenantId();
    return new ActiveInboundConnectorResponse(
        connector.executableId(),
        type,
        tenantId,
        elements.stream().map(InboundConnectorElement::element).toList(),
        getData(connector),
        connector.health());
  }
}
