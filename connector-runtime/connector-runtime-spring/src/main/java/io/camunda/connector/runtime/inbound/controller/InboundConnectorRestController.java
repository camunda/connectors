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
import io.camunda.connector.runtime.inbound.executable.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundConnectorRestController {

  private final InboundExecutableRegistry executableRegistry;
  private final ConnectorDataMapper connectorDataMapper = new ConnectorDataMapper();

  public InboundConnectorRestController(InboundExecutableRegistry executableRegistry) {
    this.executableRegistry = executableRegistry;
  }

  @GetMapping("/inbound")
  public List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      @RequestParam(required = false, value = "bpmnProcessId") String bpmnProcessId,
      @RequestParam(required = false, value = "elementId") String elementId,
      @RequestParam(required = false, value = "type") String type) {
    return getActiveInboundConnectors(bpmnProcessId, elementId, type, null);
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
        .map(
            response ->
                connectorDataMapper.createActiveInboundConnectorResponse(
                    response, ConnectorDataMapper.WEBHOOK_MAPPER))
        .collect(Collectors.toList());
  }
}
