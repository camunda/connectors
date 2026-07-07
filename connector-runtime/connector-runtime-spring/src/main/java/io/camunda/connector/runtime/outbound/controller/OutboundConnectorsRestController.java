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
package io.camunda.connector.runtime.outbound.controller;

import static io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient.X_CAMUNDA_FORWARDED_FOR;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.instances.service.InstanceForwardingRouter;
import io.camunda.connector.runtime.instances.service.OutboundConnectorsService;
import io.camunda.connector.runtime.metrics.ConnectorMetricsAggregator;
import io.camunda.connector.runtime.metrics.OutboundConnectorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/outbound")
public class OutboundConnectorsRestController {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorsRestController.class);

  private final InstanceForwardingRouter instanceForwardingRouter;
  private final OutboundConnectorsService outboundConnectorsService;
  // null when MeterRegistry is not in the application context (e.g. no Actuator)
  private final MeterRegistry meterRegistry;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  public OutboundConnectorsRestController(
      InstanceForwardingRouter instanceForwardingRouter,
      OutboundConnectorsService outboundConnectorsService,
      Optional<MeterRegistry> meterRegistry) {
    this.instanceForwardingRouter = instanceForwardingRouter;
    this.outboundConnectorsService = outboundConnectorsService;
    this.meterRegistry = meterRegistry.orElse(null);
    if (this.meterRegistry == null) {
      LOG.warn(
          "No MeterRegistry bean found — outbound metrics endpoints will return empty results. "
              + "Add spring-boot-starter-actuator to enable metrics.");
    }
  }

  @GetMapping
  public List<OutboundConnectorResponse> getOutboundConnectors(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () -> outboundConnectorsService.findAll(hostname),
        new TypeReference<>() {});
  }

  @GetMapping("/{type}")
  public List<OutboundConnectorResponse> getOutboundConnectorByType(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor,
      @PathVariable(name = "type") String type) {
    return Optional.ofNullable(
            instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
                request,
                forwardedFor,
                () -> outboundConnectorsService.findByType(type, hostname),
                new TypeReference<>() {}))
        .orElseThrow(() -> new DataNotFoundException(OutboundConnectorResponse.class, type));
  }

  /** Returns aggregated outbound connector metrics across all connector types. */
  @GetMapping("/metrics")
  public List<OutboundConnectorMetrics> getMetrics(
      HttpServletRequest request,
      @RequestHeader(name = X_CAMUNDA_FORWARDED_FOR, required = false) String forwardedFor) {
    return instanceForwardingRouter.forwardToInstancesAndReduceOrLocal(
        request,
        forwardedFor,
        () ->
            meterRegistry == null
                ? List.of()
                : List.of(ConnectorMetricsAggregator.outbound(meterRegistry, null, hostname)),
        new TypeReference<>() {});
  }

  /**
   * Returns outbound connector metrics for a specific connector type.
   *
   * @param connectorType connector type (e.g. {@code io.camunda:http-json:1})
   */
  @GetMapping("/metrics/{connectorType}")
  public List<OutboundConnectorMetrics> getMetricsByType(
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
                    ConnectorMetricsAggregator.outbound(meterRegistry, connectorType, hostname)),
        new TypeReference<>() {});
  }
}
