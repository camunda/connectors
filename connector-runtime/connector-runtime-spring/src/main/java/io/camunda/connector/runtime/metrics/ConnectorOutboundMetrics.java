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

import io.camunda.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralises all outbound connector metric recording — counters, timers, and last-activity
 * timestamps — into a single object, mirroring the approach used by {@link
 * ConnectorsInboundMetrics} on the inbound side.
 *
 * <p>Counter and timer recording is delegated to the underlying {@link MetricsRecorder} (from the
 * Camunda client). Timestamp gauges ({@code last-completed} / {@code last-failed}) are managed
 * directly via the {@link MeterRegistry}.
 */
public class ConnectorOutboundMetrics {

  private final MetricsRecorder delegate;
  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, AtomicLong> lastCompleted = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> lastFailed = new ConcurrentHashMap<>();

  public ConnectorOutboundMetrics(MetricsRecorder delegate, MeterRegistry meterRegistry) {
    this.delegate = delegate;
    this.meterRegistry = meterRegistry;
  }

  // -------------------------------------------------------------------------
  // Counter / timer delegation
  // -------------------------------------------------------------------------

  public void increaseActivated(MetricsRecorder.CounterMetricsContext ctx) {
    delegate.increaseActivated(ctx);
  }

  public void increaseFailed(MetricsRecorder.CounterMetricsContext ctx) {
    delegate.increaseFailed(ctx);
  }

  public void executeWithTimer(
      MetricsRecorder.TimerMetricsContext ctx, java.util.concurrent.Callable<Void> callable)
      throws Exception {
    delegate.executeWithTimer(ctx, callable);
  }

  // -------------------------------------------------------------------------
  // Timestamp gauges
  // -------------------------------------------------------------------------

  /** Records the current time as the last-completed timestamp for {@code connectorType}. */
  public void recordCompleted(String connectorType) {
    getOrCreate(lastCompleted, ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED, connectorType)
        .set(System.currentTimeMillis());
  }

  /** Records the current time as the last-failed timestamp for {@code connectorType}. */
  public void recordFailed(String connectorType) {
    getOrCreate(lastFailed, ConnectorMetrics.Outbound.METRIC_NAME_LAST_FAILED, connectorType)
        .set(System.currentTimeMillis());
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  /**
   * Returns the {@link MetricsRecorder} delegate. Used by {@link
   * io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory} which requires the raw
   * recorder.
   */
  public MetricsRecorder delegate() {
    return delegate;
  }

  private AtomicLong getOrCreate(
      ConcurrentHashMap<String, AtomicLong> map, String metricName, String connectorType) {
    return map.computeIfAbsent(
        connectorType,
        type -> {
          AtomicLong gauge = new AtomicLong(0);
          Gauge.builder(metricName, gauge, AtomicLong::doubleValue)
              .tag(ConnectorMetrics.Tag.TYPE, type)
              .register(meterRegistry);
          return gauge;
        });
  }
}
