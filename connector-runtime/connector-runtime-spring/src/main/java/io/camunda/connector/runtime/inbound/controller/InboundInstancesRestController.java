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
import io.camunda.connector.runtime.metrics.ConnectorMetricsAggregator;
import io.camunda.connector.runtime.metrics.InboundConnectorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOG = LoggerFactory.getLogger(InboundInstancesRestController.class);

  private final InstanceForwardingRouter instanceForwardingRouter;
  private final InboundInstancesService inboundInstancesService;
  // null when MeterRegistry is not in the application context (e.g. no Actuator)
  private final MeterRegistry meterRegistry;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  public InboundInstancesRestController(
      InstanceForwardingRouter instanceForwardingRouter,
      InboundInstancesService inboundInstancesService,
      Optional<MeterRegistry> meterRegistry) {
    this.instanceForwardingRouter = instanceForwardingRouter;
    this.inboundInstancesService = inboundInstancesService;
    this.meterRegistry = meterRegistry.orElse(null);
    if (this.meterRegistry == null) {
      LOG.warn(
          "No MeterRegistry bean found — inbound metrics endpoints will return empty results. "
              + "Add spring-boot-starter-actuator to enable metrics.");
    }
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

  @GetMapping({"/{type}/executables/{executableId}", "/executables/{executableId}"})
  public ActiveInboundConnectorResponse getConnectorInstanceExecutable(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "executableId") String executableId) {
    return Optional.ofNullable(
            instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
                request,
                forwardedFor,
                () -> inboundInstancesService.findExecutable(executableId),
                new TypeReference<>() {}))
        .orElseThrow(
            () -> new DataNotFoundException(ActiveInboundConnectorResponse.class, executableId));
  }

  @GetMapping({"/{type}/executables/{executableId}/health", "/executables/{executableId}/health"})
  public List<InstanceAwareModel.InstanceAwareHealth> getConnectorInstanceExecutableHealth(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "executableId") String executableId) {

    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> inboundInstancesService.findInstanceAwareHealth(executableId, hostname),
        new TypeReference<>() {});
  }

  @GetMapping({"/{type}/executables/{executableId}/logs", "/executables/{executableId}/logs"})
  public List<InstanceAwareModel.InstanceAwareActivity> getConnectorInstanceExecutableLogs(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "executableId") String executableId) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> inboundInstancesService.findInstanceAwareActivityLogs(executableId, hostname),
        new TypeReference<>() {});
  }

  @PostMapping("/executables/{executableId}/reset")
  public ActiveInboundConnectorResponse resetConnectorInstanceExecutable(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "executableId") String executableId) {
    return Optional.ofNullable(
            instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
                request,
                forwardedFor,
                () -> inboundInstancesService.resetExecutable(executableId),
                new TypeReference<>() {}))
        .orElseThrow(
            () -> new DataNotFoundException(ActiveInboundConnectorResponse.class, executableId));
  }

  /** Returns aggregated inbound connector metrics across all connector types. */
  @GetMapping("/metrics")
  public List<InboundConnectorMetrics> getMetrics(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () ->
            meterRegistry == null
                ? List.of()
                : List.of(ConnectorMetricsAggregator.inbound(meterRegistry, null, hostname)),
        new TypeReference<>() {});
  }

  /**
   * Returns inbound connector metrics for a specific connector type.
   *
   * @param connectorType connector type (e.g. {@code io.camunda:webhook:1})
   */
  @GetMapping("/metrics/{connectorType}")
  public List<InboundConnectorMetrics> getMetricsByType(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "connectorType") String connectorType) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () ->
            meterRegistry == null
                ? List.of()
                : List.of(
                    ConnectorMetricsAggregator.inbound(meterRegistry, connectorType, hostname)),
        new TypeReference<>() {});
  }
}
