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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
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
   * Returns outbound metrics for a specific connector type, or aggregated totals across all types
   * when {@code connectorType} is {@code null} or blank.
   */
  public static OutboundConnectorMetrics outbound(MeterRegistry registry, String connectorType) {
    if (connectorType != null && !connectorType.isBlank()) {
      return buildOutbound(registry, connectorType);
    }
    return buildOutboundAggregate(registry);
  }

  private static OutboundConnectorMetrics buildOutboundAggregate(MeterRegistry registry) {
    Set<String> types = discoverTypes(registry, null, allOutboundMetricNames());

    long completed = 0, failed = 0, bpmnError = 0;
    long jobsActivated = 0, jobsHandled = 0, streamRecreations = 0;
    double totalMs = 0.0;
    long totalCount = 0L;
    double maxMs = 0.0;
    long maxLastCompleted = 0L;
    long maxLastFailed = 0L;

    for (String type : types) {
      completed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
              type,
              ConnectorMetrics.Outbound.ACTION_COMPLETED);
      failed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
              type,
              ConnectorMetrics.Outbound.ACTION_FAILED);
      bpmnError +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
              type,
              ConnectorMetrics.Outbound.ACTION_BPMN_ERROR);
      jobsActivated +=
          (long)
              sumCounter(
                  registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED, type);
      jobsHandled +=
          (long)
              sumCounter(registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED, type);
      streamRecreations +=
          (long)
              sumCounter(
                  registry,
                  ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED,
                  type);
      for (Timer t :
          registry
              .find(ConnectorMetrics.Outbound.METRIC_NAME_TIME)
              .tag(ConnectorMetrics.Tag.TYPE, type)
              .timers()) {
        totalMs += t.totalTime(TimeUnit.MILLISECONDS);
        totalCount += t.count();
        maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
      }
      maxLastCompleted =
          Math.max(
              maxLastCompleted,
              readGauge(registry, ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED, type));
      maxLastFailed =
          Math.max(
              maxLastFailed,
              readGauge(registry, ConnectorMetrics.Outbound.METRIC_NAME_LAST_FAILED, type));
    }

    OutboundConnectorMetrics.ExecutionTime executionTime =
        totalCount > 0
            ? new OutboundConnectorMetrics.ExecutionTime(totalMs / totalCount, maxMs)
            : null;

    return new OutboundConnectorMetrics(
        null,
        null,
        null,
        new OutboundConnectorMetrics.Jobs(completed, failed, bpmnError),
        executionTime,
        new OutboundConnectorMetrics.WorkerStats(jobsActivated, jobsHandled, streamRecreations),
        epochMsToInstant(maxLastCompleted),
        epochMsToInstant(maxLastFailed),
        readRuntimeUptime(registry));
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

    OutboundConnectorMetrics.Jobs jobs = buildJobs(registry, type);
    OutboundConnectorMetrics.ExecutionTime executionTime = buildExecutionTime(registry, type);
    OutboundConnectorMetrics.WorkerStats worker = buildWorkerStats(registry, type);
    Instant lastCompleted =
        epochMsToInstant(
            readGauge(registry, ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED, type));
    Instant lastFailed =
        epochMsToInstant(
            readGauge(registry, ConnectorMetrics.Outbound.METRIC_NAME_LAST_FAILED, type));

    return new OutboundConnectorMetrics(
        null,
        templateId,
        templateVersion,
        jobs,
        executionTime,
        worker,
        lastCompleted,
        lastFailed,
        readRuntimeUptime(registry));
  }

  private static OutboundConnectorMetrics.Jobs buildJobs(MeterRegistry registry, String type) {
    return new OutboundConnectorMetrics.Jobs(
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
   * Returns inbound metrics for a specific connector type, or aggregated totals across all types
   * when {@code connectorType} is {@code null} or blank.
   */
  public static InboundConnectorMetrics inbound(MeterRegistry registry, String connectorType) {
    if (connectorType != null && !connectorType.isBlank()) {
      return buildInbound(registry, connectorType);
    }
    return buildInboundAggregate(registry);
  }

  private static InboundConnectorMetrics buildInboundAggregate(MeterRegistry registry) {
    Set<String> types = discoverTypes(registry, null, allInboundMetricNames());

    long activated = 0, deactivated = 0, activationFailed = 0;
    long triggered = 0, correlated = 0, correlationFailed = 0, activationConditionFailed = 0;
    long maxLastActivated = 0L;
    long maxLastTriggered = 0L;

    for (String type : types) {
      activated +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
              type,
              ConnectorMetrics.Inbound.ACTION_ACTIVATED);
      deactivated +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
              type,
              ConnectorMetrics.Inbound.ACTION_DEACTIVATED);
      activationFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
              type,
              ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED);
      triggered +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_TRIGGERED);
      correlated +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_CORRELATED);
      correlationFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED);
      activationConditionFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED);
      maxLastActivated =
          Math.max(
              maxLastActivated,
              readGauge(registry, ConnectorMetrics.Inbound.METRIC_NAME_LAST_ACTIVATED, type));
      maxLastTriggered =
          Math.max(
              maxLastTriggered,
              readGauge(registry, ConnectorMetrics.Inbound.METRIC_NAME_LAST_TRIGGERED, type));
    }

    return new InboundConnectorMetrics(
        null,
        new InboundConnectorMetrics.Activations(activated, deactivated, activationFailed),
        new InboundConnectorMetrics.Triggers(
            triggered, correlated, correlationFailed, activationConditionFailed),
        epochMsToInstant(maxLastActivated),
        epochMsToInstant(maxLastTriggered),
        readRuntimeUptime(registry));
  }

  private static InboundConnectorMetrics buildInbound(MeterRegistry registry, String type) {
    return new InboundConnectorMetrics(
        null,
        buildActivations(registry, type),
        buildTriggers(registry, type),
        epochMsToInstant(
            readGauge(registry, ConnectorMetrics.Inbound.METRIC_NAME_LAST_ACTIVATED, type)),
        epochMsToInstant(
            readGauge(registry, ConnectorMetrics.Inbound.METRIC_NAME_LAST_TRIGGERED, type)),
        readRuntimeUptime(registry));
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
   * Reads the {@code process.uptime} gauge (registered by Spring Boot Actuator) and returns the
   * uptime in whole seconds, or {@code null} if the metric is not available.
   */
  private static Long readRuntimeUptime(MeterRegistry registry) {
    TimeGauge timeGauge = registry.find("process.uptime").timeGauge();
    if (timeGauge != null) {
      return (long) timeGauge.value(TimeUnit.SECONDS);
    }
    // Fall back to a plain Gauge (some test registries expose it this way)
    Gauge gauge = registry.find("process.uptime").gauge();
    return gauge != null ? (long) gauge.value() : null;
  }

  /**
   * Reads the value of a {@link Gauge} tagged with the given connector type. Returns {@code 0} if
   * no gauge is registered for that type.
   */
  private static long readGauge(MeterRegistry registry, String metricName, String type) {
    return registry.find(metricName).tag(ConnectorMetrics.Tag.TYPE, type).gauges().stream()
        .mapToLong(g -> (long) g.value())
        .max()
        .orElse(0L);
  }

  /**
   * Converts an epoch-millisecond value to an {@link Instant}, returning {@code null} when the
   * value is {@code 0} (meaning "never recorded").
   */
  private static Instant epochMsToInstant(long epochMs) {
    return epochMs > 0 ? Instant.ofEpochMilli(epochMs) : null;
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
        ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
        ConnectorMetrics.Inbound.METRIC_NAME_LAST_ACTIVATED,
        ConnectorMetrics.Inbound.METRIC_NAME_LAST_TRIGGERED);
  }
}
