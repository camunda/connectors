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
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.MetricResponse;
import io.camunda.connector.runtime.metrics.MetricsQueryHelper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/outbound")
public class OutboundConnectorsRestController {

  private static final List<String> CURATED_METRICS =
      List.of(
          ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
          ConnectorMetrics.Outbound.METRIC_NAME_TIME,
          ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED,
          ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED,
          ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED);

  private final InstanceForwardingRouter instanceForwardingRouter;
  private final OutboundConnectorsService outboundConnectorsService;
  private final MeterRegistry meterRegistry;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  public OutboundConnectorsRestController(
      InstanceForwardingRouter instanceForwardingRouter,
      OutboundConnectorsService outboundConnectorsService,
      MeterRegistry meterRegistry) {
    this.instanceForwardingRouter = instanceForwardingRouter;
    this.outboundConnectorsService = outboundConnectorsService;
    this.meterRegistry = meterRegistry;
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

  /**
   * Returns outbound connector metrics, optionally filtered by name and tags.
   *
   * <p>When no {@code name} is provided, a curated set of outbound metrics is returned: invocations
   * and execution-time. Tags are provided as {@code key:value} pairs and applied to every requested
   * metric.
   *
   * @param names optional metric names to query (e.g. {@code
   *     camunda.connector.outbound.invocations})
   * @param tags optional {@code key:value} tag filters
   */
  @GetMapping("/metrics")
  public List<MetricResponse> getMetrics(
      @RequestParam(name = "name", required = false) List<String> names,
      @RequestParam(name = "tag", required = false) List<String> tags) {
    return MetricsQueryHelper.queryMetrics(meterRegistry, names, tags, CURATED_METRICS);
  }
}
