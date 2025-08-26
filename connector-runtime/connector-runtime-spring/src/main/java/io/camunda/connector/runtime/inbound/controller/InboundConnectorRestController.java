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

import static io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient.X_CAMUNDA_FORWARDED_FOR;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.inbound.executable.*;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import io.camunda.connector.runtime.instances.service.InstanceForwardingRouter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InboundConnectorRestController {

  private final InboundExecutableRegistry executableRegistry;
  private final ConnectorDataMapper connectorDataMapper = new ConnectorDataMapper();
  private final InstanceForwardingRouter instanceForwardingRouter;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  public InboundConnectorRestController(
      InboundExecutableRegistry executableRegistry,
      @Autowired(required = false) InstanceForwardingRouter instanceForwardingRouter) {
    this.executableRegistry = executableRegistry;
    this.instanceForwardingRouter = instanceForwardingRouter;
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
  public List<Collection<InstanceAwareModel.InstanceAwareActivity>> getActiveInboundConnectorLogs(
      @PathVariable(value = "tenantId") String tenantId,
      @PathVariable(value = "bpmnProcessId") String bpmnProcessId,
      @PathVariable(value = "elementId") String elementId,
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> getActivityLogs(tenantId, bpmnProcessId, elementId, hostname),
        new TypeReference<>() {});
  }

  private List<Collection<InstanceAwareModel.InstanceAwareActivity>> getActivityLogs(
      String tenantId, String bpmnProcessId, String elementId, String hostname) {
    var result =
        executableRegistry.query(
            new ActiveExecutableQuery(bpmnProcessId, elementId, null, tenantId));
    return result.stream()
        .map(ActiveExecutableResponse::logs)
        .filter(Predicate.not(Collection::isEmpty))
        .map(
            activities ->
                activities.stream()
                    .map(
                        activity -> {
                          return new InstanceAwareModel.InstanceAwareActivity(
                              activity.severity(),
                              activity.tag(),
                              activity.timestamp(),
                              activity.message(),
                              hostname);
                        })
                    .toList())
        .collect(Collectors.toList());
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
