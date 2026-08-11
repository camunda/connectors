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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises the physical-tenant dimension added to the outbound metrics pipeline (#7965): two
 * physical tenants (engines) executing the same connector type must land in separate,
 * correctly-tagged meters, and {@link ConnectorMetricsAggregator#outbound} must sum across all
 * tenants when unfiltered and scope down correctly when a {@code physicalTenantIds} filter is
 * given.
 */
class ConnectorOutboundMetricsPhysicalTenantTest {

  private static final String TYPE = "io.camunda:http-json:1";

  private static ActivatedJob job() {
    var job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn(TYPE);
    when(job.getCustomHeaders())
        .thenReturn(Map.of("elementTemplateId", "template", "elementTemplateVersion", "1"));
    return job;
  }

  private static void recordCompletedJob(MeterRegistry registry, String physicalTenantId) {
    new MicrometerMetricsRecorder(registry)
        .increaseCompleted(ConnectorMetrics.counter(job(), physicalTenantId));
  }

  private static long completedFor(MeterRegistry registry, String physicalTenantId) {
    return (long)
        registry
            .find(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
            .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_COMPLETED)
            .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, physicalTenantId)
            .counter()
            .count();
  }

  @Test
  void sameConnectorTypeAcrossTwoPhysicalTenants_recordsSeparatelyTaggedCounters() {
    var registry = new SimpleMeterRegistry();

    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-b");

    assertThat(completedFor(registry, "tenant-a")).isEqualTo(2);
    assertThat(completedFor(registry, "tenant-b")).isEqualTo(1);
  }

  @Test
  void jobWithoutResolvedPhysicalTenant_fallsBackToDefaultTag() {
    var registry = new SimpleMeterRegistry();

    recordCompletedJob(registry, null);

    assertThat(completedFor(registry, ConnectorMetrics.DEFAULT_PHYSICAL_TENANT_ID)).isEqualTo(1);
  }

  @Test
  void tenantLessOverloads_attributeTheJobToItsOwnPhysicalTenant() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerMetricsRecorder(registry);
    var job = job();
    when(job.getPhysicalTenantId()).thenReturn("tenant-from-job");

    // the pre-#7965 signatures, kept for callers compiled against them
    recorder.increaseCompleted(ConnectorMetrics.counter(job));
    var timerTags = ConnectorMetrics.timer(job).tags();

    assertThat(completedFor(registry, "tenant-from-job")).isEqualTo(1);
    assertThat(timerTags).containsEntry(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, "tenant-from-job");
  }

  @Test
  void lastCompletedGauge_isTaggedPerPhysicalTenant() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerMetricsRecorder(registry);

    new ConnectorOutboundMetrics(recorder, registry, "tenant-a").recordCompleted(TYPE);
    new ConnectorOutboundMetrics(recorder, registry, "tenant-b").recordCompleted(TYPE);

    // one gauge per (type, physical tenant) — before #7965 the second registration silently
    // collided with the first one's, leaving that engine's timestamp invisible
    assertThat(registry.find(ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED).gauges())
        .hasSize(2);
    assertThat(
            registry
                .find(ConnectorMetrics.Outbound.METRIC_NAME_LAST_COMPLETED)
                .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, "tenant-b")
                .gauge()
                .value())
        .isPositive();
  }

  @Test
  void aggregatorOutbound_withoutFilter_sumsAcrossAllPhysicalTenants() {
    var registry = new SimpleMeterRegistry();
    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-b");

    assertThat(
            ConnectorMetricsAggregator.outbound(registry, TYPE, null, "runtime-1")
                .job()
                .completed())
        .isEqualTo(2);
    assertThat(
            ConnectorMetricsAggregator.outbound(registry, null, null, "runtime-1")
                .job()
                .completed())
        .isEqualTo(2);
  }

  @Test
  void aggregatorOutbound_withFilter_scopesToMatchingPhysicalTenantsOnly() {
    var registry = new SimpleMeterRegistry();
    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-b");

    var tenantAOnly =
        ConnectorMetricsAggregator.outbound(registry, TYPE, List.of("tenant-a"), "runtime-1");
    var bothTenants =
        ConnectorMetricsAggregator.outbound(
            registry, TYPE, List.of("tenant-a", "tenant-b"), "runtime-1");
    var unknownTenant =
        ConnectorMetricsAggregator.outbound(
            registry, TYPE, List.of("nonexistent-tenant"), "runtime-1");

    assertThat(tenantAOnly.job().completed()).isEqualTo(2);
    assertThat(bothTenants.job().completed()).isEqualTo(3);
    assertThat(unknownTenant.job().completed()).isZero();
  }

  @Test
  void aggregatorOutbound_aggregateAcrossTypes_appliesFilterConsistently() {
    var registry = new SimpleMeterRegistry();
    recordCompletedJob(registry, "tenant-a");
    recordCompletedJob(registry, "tenant-b");

    // connectorType == null -> aggregate across all discovered types
    var unfiltered = ConnectorMetricsAggregator.outbound(registry, null, null, "runtime-1");
    var filtered =
        ConnectorMetricsAggregator.outbound(registry, null, List.of("tenant-a"), "runtime-1");

    assertThat(unfiltered.job().completed()).isEqualTo(2);
    assertThat(filtered.job().completed()).isEqualTo(1);
  }

  @Test
  void aggregatorOutbound_scopesLastCompletedGaugeToTheFilteredPhysicalTenant() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerMetricsRecorder(registry);
    new ConnectorOutboundMetrics(recorder, registry, "tenant-a").recordCompleted(TYPE);

    var tenantA =
        ConnectorMetricsAggregator.outbound(registry, TYPE, List.of("tenant-a"), "runtime-1");
    var tenantB =
        ConnectorMetricsAggregator.outbound(registry, TYPE, List.of("tenant-b"), "runtime-1");

    assertThat(tenantA.job().lastCompleted()).isNotNull();
    assertThat(tenantB.job().lastCompleted()).isNull();
  }

  @Test
  void aggregatorOutbound_omitsWorkerSection_whenFiltered() {
    var registry = new SimpleMeterRegistry();
    recordCompletedJob(registry, "tenant-a");

    // the camunda.client.worker.* counters carry no physical-tenant tag, so they cannot be
    // attributed to one engine — the section is omitted rather than reported unfiltered or as 0
    assertThat(
            ConnectorMetricsAggregator.outbound(registry, TYPE, List.of("tenant-a"), "runtime-1")
                .worker())
        .isNull();
    assertThat(
            ConnectorMetricsAggregator.outbound(registry, null, List.of("tenant-a"), "runtime-1")
                .worker())
        .isNull();
    assertThat(ConnectorMetricsAggregator.outbound(registry, TYPE, null, "runtime-1").worker())
        .isNotNull();
  }
}
