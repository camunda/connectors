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
package io.camunda.connector.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Queries a {@link MeterRegistry} and builds structured {@link OutboundConnectorMetrics} and {@link
 * InboundConnectorMetrics} aggregates, grouped by connector type.
 */
public final class ConnectorMetricsAggregator {

  private ConnectorMetricsAggregator() {}

  // -------------------------------------------------------------------------
  // Outbound
  // -------------------------------------------------------------------------

  /**
   * Returns aggregated outbound metrics for all registered connector types, optionally filtered to
   * a single {@code connectorType}.
   */
  public static List<OutboundConnectorMetrics> outbound(
      MeterRegistry registry, String connectorType) {
    Set<String> types = discoverTypes(registry, connectorType, allOutboundMetricNames());
    List<OutboundConnectorMetrics> result = new ArrayList<>(types.size());
    for (String type : types) {
      result.add(buildOutbound(registry, type));
    }
    return result;
  }

  private static OutboundConnectorMetrics buildOutbound(MeterRegistry registry, String type) {
    String templateId =
        findTagValue(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
            type,
            ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID);
    String templateVersion =
        findTagValue(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
            type,
            ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION);

    OutboundConnectorMetrics.Invocations invocations = buildInvocations(registry, type);
    OutboundConnectorMetrics.ExecutionTime executionTime = buildExecutionTime(registry, type);
    OutboundConnectorMetrics.WorkerStats worker = buildWorkerStats(registry, type);

    return new OutboundConnectorMetrics(
        type, templateId, templateVersion, invocations, executionTime, worker);
  }

  private static OutboundConnectorMetrics.Invocations buildInvocations(
      MeterRegistry registry, String type) {
    return new OutboundConnectorMetrics.Invocations(
        sumCounterByAction(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
            type,
            ConnectorMetrics.Outbound.ACTION_COMPLETED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
            type,
            ConnectorMetrics.Outbound.ACTION_FAILED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
            type,
            ConnectorMetrics.Outbound.ACTION_BPMN_ERROR));
  }

  private static OutboundConnectorMetrics.ExecutionTime buildExecutionTime(
      MeterRegistry registry, String type) {
    List<Timer> timers =
        registry
            .find(ConnectorMetrics.Outbound.METRIC_NAME_TIME)
            .tag(ConnectorMetrics.Tag.TYPE, type)
            .timers()
            .stream()
            .toList();

    if (timers.isEmpty()) {
      return null;
    }

    double totalMs = 0.0;
    long totalCount = 0L;
    double maxMs = 0.0;

    for (Timer t : timers) {
      totalMs += t.totalTime(TimeUnit.MILLISECONDS);
      totalCount += t.count();
      maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
    }

    double meanMs = totalCount > 0 ? totalMs / totalCount : 0.0;
    return new OutboundConnectorMetrics.ExecutionTime(meanMs, maxMs);
  }

  private static OutboundConnectorMetrics.WorkerStats buildWorkerStats(
      MeterRegistry registry, String type) {
    return new OutboundConnectorMetrics.WorkerStats(
        (long)
            sumCounter(registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED, type),
        (long) sumCounter(registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED, type),
        (long)
            sumCounter(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED,
                type));
  }

  // -------------------------------------------------------------------------
  // Inbound
  // -------------------------------------------------------------------------

  /**
   * Returns aggregated inbound metrics for all registered connector types, optionally filtered to a
   * single {@code connectorType}.
   */
  public static List<InboundConnectorMetrics> inbound(
      MeterRegistry registry, String connectorType) {
    Set<String> types = discoverTypes(registry, connectorType, allInboundMetricNames());
    List<InboundConnectorMetrics> result = new ArrayList<>(types.size());
    for (String type : types) {
      result.add(buildInbound(registry, type));
    }
    return result;
  }

  private static InboundConnectorMetrics buildInbound(MeterRegistry registry, String type) {
    return new InboundConnectorMetrics(
        type, buildActivations(registry, type), buildTriggers(registry, type));
  }

  private static InboundConnectorMetrics.Activations buildActivations(
      MeterRegistry registry, String type) {
    return new InboundConnectorMetrics.Activations(
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
            type,
            ConnectorMetrics.Inbound.ACTION_ACTIVATED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
            type,
            ConnectorMetrics.Inbound.ACTION_DEACTIVATED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
            type,
            ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED));
  }

  private static InboundConnectorMetrics.Triggers buildTriggers(
      MeterRegistry registry, String type) {
    return new InboundConnectorMetrics.Triggers(
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
            type,
            ConnectorMetrics.Inbound.ACTION_TRIGGERED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
            type,
            ConnectorMetrics.Inbound.ACTION_CORRELATED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
            type,
            ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED),
        sumCounterByAction(
            registry,
            ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
            type,
            ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static Set<String> discoverTypes(
      MeterRegistry registry, String connectorTypeFilter, List<String> metricNames) {
    Set<String> types = new TreeSet<>();
    for (String metricName : metricNames) {
      for (Meter meter : registry.find(metricName).meters()) {
        String type = meter.getId().getTag(ConnectorMetrics.Tag.TYPE);
        if (type != null) {
          types.add(type);
        }
      }
    }
    if (connectorTypeFilter != null && !connectorTypeFilter.isBlank()) {
      types.retainAll(Set.of(connectorTypeFilter));
    }
    return types;
  }

  private static long sumCounterByAction(
      MeterRegistry registry, String metricName, String type, String action) {
    double sum =
        registry
            .find(metricName)
            .tag(ConnectorMetrics.Tag.TYPE, type)
            .tag(ConnectorMetrics.Tag.ACTION, action)
            .counters()
            .stream()
            .mapToDouble(Counter::count)
            .sum();
    return (long) sum;
  }

  private static double sumCounter(MeterRegistry registry, String metricName, String type) {
    return registry.find(metricName).tag(ConnectorMetrics.Tag.TYPE, type).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  /**
   * Finds the value of {@code tagKey} from the first meter registered for {@code metricName} with
   * the given connector type, or {@code null} if no meter is found.
   */
  private static String findTagValue(
      MeterRegistry registry, String metricName, String type, String tagKey) {
    return registry.find(metricName).tag(ConnectorMetrics.Tag.TYPE, type).meters().stream()
        .map(m -> m.getId().getTag(tagKey))
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
  }

  private static List<String> allOutboundMetricNames() {
    return List.of(
        ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
        ConnectorMetrics.Outbound.METRIC_NAME_TIME,
        ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED,
        ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED,
        ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED);
  }

  private static List<String> allInboundMetricNames() {
    return List.of(
        ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
        ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS);
  }
}
