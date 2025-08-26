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
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.inbound.executable.*;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import io.camunda.connector.runtime.instances.service.InboundInstancesService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingRouter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

  private final InstanceForwardingRouter instanceForwardingRouter;
  private final InboundInstancesService inboundInstancesService;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  public InboundInstancesRestController(
      @Autowired(required = false) InstanceForwardingRouter instanceForwardingRouter,
      InboundInstancesService inboundInstancesService) {
    this.instanceForwardingRouter = instanceForwardingRouter;
    this.inboundInstancesService = inboundInstancesService;
  }

  @GetMapping()
  public List<ConnectorInstances> getConnectorInstances(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        inboundInstancesService::findAllConnectorInstances,
        new TypeReference<>() {});
  }

  @GetMapping("/{type}")
  public ConnectorInstances getConnectorInstance(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "type") String type) {
    return Optional.ofNullable(
            instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
                request,
                forwardedFor,
                () -> inboundInstancesService.findConnectorInstancesOfType(type),
                new TypeReference<>() {}))
        .orElseThrow(() -> new DataNotFoundException(ConnectorInstances.class, type));
  }

  @GetMapping("/{type}/executables/{executableId}")
  public ActiveInboundConnectorResponse getConnectorInstanceExecutable(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "type") String type,
      @PathVariable(name = "executableId") String executableId) {
    return Optional.ofNullable(
            instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
                request,
                forwardedFor,
                () -> inboundInstancesService.findExecutable(type, executableId),
                new TypeReference<>() {}))
        .orElseThrow(
            () -> new DataNotFoundException(ActiveInboundConnectorResponse.class, executableId));
  }

  @GetMapping("/{type}/executables/{executableId}/health")
  public List<InstanceAwareModel.InstanceAwareHealth> getConnectorInstanceExecutableHealth(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "type") String type,
      @PathVariable(name = "executableId") String executableId) {

    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> inboundInstancesService.findInstanceAwareHealth(type, executableId, hostname),
        new TypeReference<>() {});
  }

  @GetMapping("/{type}/executables/{executableId}/logs")
  public List<InstanceAwareModel.InstanceAwareActivity> getConnectorInstanceExecutableLogs(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "type") String type,
      @PathVariable(name = "executableId") String executableId) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> inboundInstancesService.findInstanceAwareActivityLogs(type, executableId, hostname),
        new TypeReference<>() {});
  }
}
