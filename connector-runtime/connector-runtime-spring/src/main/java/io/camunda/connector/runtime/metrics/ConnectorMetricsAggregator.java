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
   * when {@code connectorType} is {@code null} or blank. {@code runtimeId} identifies the runtime
   * node that produced these metrics.
   */
  public static OutboundConnectorMetrics outbound(
      MeterRegistry registry, String connectorType, String runtimeId) {
    return outbound(registry, connectorType, null, runtimeId);
  }

  /**
   * @param physicalTenantIds when non-null and non-empty, restricts the sums to meters recorded for
   *     one of these physical tenants (engines) — a distinct dimension from connector {@code type}.
   *     {@code null}/empty sums across every physical tenant, matching the pre-existing behavior of
   *     this method.
   *     <p>The {@link OutboundConnectorMetrics.Worker} section is left {@code null} whenever a
   *     filter is applied: those counters ({@code camunda.client.worker.*}) are recorded by the
   *     Camunda client itself, which tags them by job type only, so they cannot be attributed to a
   *     physical tenant. Reporting the unfiltered pod-wide totals inside an otherwise filtered
   *     response would misattribute them, and reporting zeros would read as "no jobs activated".
   */
  public static OutboundConnectorMetrics outbound(
      MeterRegistry registry,
      String connectorType,
      List<String> physicalTenantIds,
      String runtimeId) {
    if (registry == null) {
      return new OutboundConnectorMetrics(runtimeId, null, null, null);
    }
    if (connectorType != null && !connectorType.isBlank()) {
      return buildOutbound(registry, connectorType, physicalTenantIds, runtimeId);
    }
    return buildOutboundAggregate(registry, physicalTenantIds, runtimeId);
  }

  /**
   * @return {@code true} when no physical-tenant filter was requested.
   */
  private static boolean isUnfiltered(List<String> physicalTenantIds) {
    return physicalTenantIds == null || physicalTenantIds.isEmpty();
  }

  private static OutboundConnectorMetrics buildOutboundAggregate(
      MeterRegistry registry, List<String> physicalTenantIds, String runtimeId) {
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
              ConnectorMetrics.Outbound.ACTION_COMPLETED,
              physicalTenantIds);
      failed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
              type,
              ConnectorMetrics.Outbound.ACTION_FAILED,
              physicalTenantIds);
      bpmnError +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
              type,
              ConnectorMetrics.Outbound.ACTION_BPMN_ERROR,
              physicalTenantIds);
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
        if (!matchesPhysicalTenantIds(t.getId(), physicalTenantIds)) {
          continue;
        }
        totalMs += t.totalTime(TimeUnit.MILLISECONDS);
        totalCount += t.count();
      }
      maxMs =
          Math.max(
              maxMs,
              readGauge(
                  registry,
                  ConnectorMetrics.Outbound.METRIC_NAME_MAX_EXECUTION_TIME,
                  type,
                  physicalTenantIds));
      maxLastCompleted =
          Math.max(
              maxLastCompleted,
              readGauge(
                  registry,
                  ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED,
                  type,
                  physicalTenantIds));
      maxLastFailed =
          Math.max(
              maxLastFailed,
              readGauge(
                  registry,
                  ConnectorMetrics.Outbound.METRIC_NAME_LAST_FAILED,
                  type,
                  physicalTenantIds));
    }

    OutboundConnectorMetrics.ExecutionTime executionTime =
        totalCount > 0
            ? new OutboundConnectorMetrics.ExecutionTime(totalMs / totalCount, maxMs)
            : null;

    return new OutboundConnectorMetrics(
        runtimeId,
        new OutboundConnectorMetrics.Runtime(readRuntimeUptime(registry)),
        new OutboundConnectorMetrics.Job(
            completed,
            failed,
            bpmnError,
            executionTime,
            epochMsToInstant(maxLastCompleted),
            epochMsToInstant(maxLastFailed)),
        isUnfiltered(physicalTenantIds)
            ? new OutboundConnectorMetrics.Worker(jobsActivated, jobsHandled, streamRecreations)
            : null);
  }

  private static OutboundConnectorMetrics buildOutbound(
      MeterRegistry registry, String type, List<String> physicalTenantIds, String runtimeId) {
    OutboundConnectorMetrics.ExecutionTime executionTime =
        buildExecutionTime(registry, type, physicalTenantIds);
    Instant lastCompleted =
        epochMsToInstant(
            readGauge(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED,
                type,
                physicalTenantIds));
    Instant lastFailed =
        epochMsToInstant(
            readGauge(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_LAST_FAILED,
                type,
                physicalTenantIds));

    return new OutboundConnectorMetrics(
        runtimeId,
        new OutboundConnectorMetrics.Runtime(readRuntimeUptime(registry)),
        new OutboundConnectorMetrics.Job(
            sumCounterByAction(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
                type,
                ConnectorMetrics.Outbound.ACTION_COMPLETED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
                type,
                ConnectorMetrics.Outbound.ACTION_FAILED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
                type,
                ConnectorMetrics.Outbound.ACTION_BPMN_ERROR,
                physicalTenantIds),
            executionTime,
            lastCompleted,
            lastFailed),
        isUnfiltered(physicalTenantIds)
            ? new OutboundConnectorMetrics.Worker(
                (long)
                    sumCounter(
                        registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED, type),
                (long)
                    sumCounter(
                        registry, ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED, type),
                (long)
                    sumCounter(
                        registry,
                        ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED,
                        type))
            : null);
  }

  private static OutboundConnectorMetrics.ExecutionTime buildExecutionTime(
      MeterRegistry registry, String type, List<String> physicalTenantIds) {
    List<Timer> timers =
        registry
            .find(ConnectorMetrics.Outbound.METRIC_NAME_TIME)
            .tag(ConnectorMetrics.Tag.TYPE, type)
            .timers()
            .stream()
            .filter(timer -> matchesPhysicalTenantIds(timer.getId(), physicalTenantIds))
            .toList();

    if (timers.isEmpty()) {
      return null;
    }

    double totalMs = 0.0;
    long totalCount = 0L;

    for (Timer t : timers) {
      totalMs += t.totalTime(TimeUnit.MILLISECONDS);
      totalCount += t.count();
    }

    double meanMs = totalCount > 0 ? totalMs / totalCount : 0.0;
    double maxMs =
        readGauge(
            registry,
            ConnectorMetrics.Outbound.METRIC_NAME_MAX_EXECUTION_TIME,
            type,
            physicalTenantIds);
    return new OutboundConnectorMetrics.ExecutionTime(meanMs, maxMs);
  }

  // -------------------------------------------------------------------------
  // Inbound
  // -------------------------------------------------------------------------

  /**
   * Returns inbound metrics for a specific connector type, or aggregated totals across all types
   * when {@code connectorType} is {@code null} or blank. {@code runtimeId} identifies the runtime
   * node that produced these metrics.
   */
  public static InboundConnectorMetrics inbound(
      MeterRegistry registry, String connectorType, String runtimeId) {
    return inbound(registry, connectorType, null, runtimeId);
  }

  /**
   * @param physicalTenantIds when non-null and non-empty, restricts the sums to meters recorded for
   *     one of these physical tenants (engines) — a distinct dimension from connector {@code type}.
   *     {@code null}/empty sums across every physical tenant, matching the pre-existing behavior of
   *     this method.
   */
  public static InboundConnectorMetrics inbound(
      MeterRegistry registry,
      String connectorType,
      List<String> physicalTenantIds,
      String runtimeId) {
    if (registry == null) {
      return new InboundConnectorMetrics(runtimeId, null, null, null);
    }
    if (connectorType != null && !connectorType.isBlank()) {
      return buildInbound(registry, connectorType, physicalTenantIds, runtimeId);
    }
    return buildInboundAggregate(registry, physicalTenantIds, runtimeId);
  }

  private static InboundConnectorMetrics buildInboundAggregate(
      MeterRegistry registry, List<String> physicalTenantIds, String runtimeId) {
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
              ConnectorMetrics.Inbound.ACTION_ACTIVATED,
              physicalTenantIds);
      deactivated +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
              type,
              ConnectorMetrics.Inbound.ACTION_DEACTIVATED,
              physicalTenantIds);
      activationFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
              type,
              ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED,
              physicalTenantIds);
      triggered +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_TRIGGERED,
              physicalTenantIds);
      correlated +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_CORRELATED,
              physicalTenantIds);
      correlationFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED,
              physicalTenantIds);
      activationConditionFailed +=
          sumCounterByAction(
              registry,
              ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
              type,
              ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED,
              physicalTenantIds);
      maxLastActivated =
          Math.max(
              maxLastActivated,
              readGauge(
                  registry,
                  ConnectorMetrics.Inbound.METRIC_NAME_LAST_ACTIVATED,
                  type,
                  physicalTenantIds));
      maxLastTriggered =
          Math.max(
              maxLastTriggered,
              readGauge(
                  registry,
                  ConnectorMetrics.Inbound.METRIC_NAME_LAST_TRIGGERED,
                  type,
                  physicalTenantIds));
    }

    return new InboundConnectorMetrics(
        runtimeId,
        new InboundConnectorMetrics.Runtime(readRuntimeUptime(registry)),
        new InboundConnectorMetrics.Activation(
            activated, deactivated, activationFailed, epochMsToInstant(maxLastActivated)),
        new InboundConnectorMetrics.Trigger(
            triggered,
            correlated,
            correlationFailed,
            activationConditionFailed,
            epochMsToInstant(maxLastTriggered)));
  }

  private static InboundConnectorMetrics buildInbound(
      MeterRegistry registry, String type, List<String> physicalTenantIds, String runtimeId) {
    return new InboundConnectorMetrics(
        runtimeId,
        new InboundConnectorMetrics.Runtime(readRuntimeUptime(registry)),
        new InboundConnectorMetrics.Activation(
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
                type,
                ConnectorMetrics.Inbound.ACTION_ACTIVATED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
                type,
                ConnectorMetrics.Inbound.ACTION_DEACTIVATED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS,
                type,
                ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED,
                physicalTenantIds),
            epochMsToInstant(
                readGauge(
                    registry,
                    ConnectorMetrics.Inbound.METRIC_NAME_LAST_ACTIVATED,
                    type,
                    physicalTenantIds))),
        new InboundConnectorMetrics.Trigger(
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
                type,
                ConnectorMetrics.Inbound.ACTION_TRIGGERED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
                type,
                ConnectorMetrics.Inbound.ACTION_CORRELATED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
                type,
                ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED,
                physicalTenantIds),
            sumCounterByAction(
                registry,
                ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS,
                type,
                ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED,
                physicalTenantIds),
            epochMsToInstant(
                readGauge(
                    registry,
                    ConnectorMetrics.Inbound.METRIC_NAME_LAST_TRIGGERED,
                    type,
                    physicalTenantIds))));
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

  /** Physical-tenant-aware — see {@link #matchesPhysicalTenantIds}. */
  private static long sumCounterByAction(
      MeterRegistry registry,
      String metricName,
      String type,
      String action,
      List<String> physicalTenantIds) {
    double sum =
        registry
            .find(metricName)
            .tag(ConnectorMetrics.Tag.TYPE, type)
            .tag(ConnectorMetrics.Tag.ACTION, action)
            .counters()
            .stream()
            .filter(counter -> matchesPhysicalTenantIds(counter.getId(), physicalTenantIds))
            .mapToDouble(Counter::count)
            .sum();
    return (long) sum;
  }

  /**
   * @return {@code true} when {@code physicalTenantIds} is {@code null}/empty (no filtering — sums
   *     across every physical tenant, matching the pre-existing behavior of this aggregator) or the
   *     meter's own {@code physicalTenantId} tag is one of the requested values.
   */
  private static boolean matchesPhysicalTenantIds(Meter.Id id, List<String> physicalTenantIds) {
    return physicalTenantIds == null
        || physicalTenantIds.isEmpty()
        || physicalTenantIds.contains(id.getTag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID));
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
   * Reads the value of a {@link Gauge} tagged with the given connector type, restricted to the
   * requested physical tenants (see {@link #matchesPhysicalTenantIds}). Returns {@code 0} if no
   * matching gauge is registered.
   */
  private static long readGauge(
      MeterRegistry registry, String metricName, String type, List<String> physicalTenantIds) {
    return registry.find(metricName).tag(ConnectorMetrics.Tag.TYPE, type).gauges().stream()
        .filter(gauge -> matchesPhysicalTenantIds(gauge.getId(), physicalTenantIds))
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

  private static List<String> allOutboundMetricNames() {
    return List.of(
        ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS,
        ConnectorMetrics.Outbound.METRIC_NAME_TIME,
        ConnectorMetrics.Outbound.METRIC_NAME_MAX_EXECUTION_TIME,
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
