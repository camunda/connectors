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

import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises the physical-tenant dimension added to {@link ConnectorsInboundMetrics}/{@link
 * ConnectorMetricsAggregator}: two physical tenants recording the exact same connector
 * type/element-template/version must land in separate, correctly-tagged meters (not share one
 * miscounted/mistagged {@code Counter}/{@code Gauge} via {@link Result}'s cache key), and {@link
 * ConnectorMetricsAggregator#inbound} must sum across all tenants when unfiltered and scope down
 * correctly when a {@code physicalTenantIds} filter is given.
 */
class ConnectorsInboundMetricsPhysicalTenantTest {

  private static InboundConnectorElement elementFor(String physicalTenantId) {
    return new InboundConnectorElement(
        Map.of("inbound.type", "test-type"),
        new StartEventCorrelationPoint("processId", 0, 0),
        new ProcessElementWithRuntimeData(
            "processId",
            null,
            null,
            0,
            0,
            "elementId",
            null,
            null,
            "tenant",
            physicalTenantId,
            new ElementTemplateDetails("test-template", "1", "icon"),
            Map.of()));
  }

  @Test
  void sameTypeAndTemplateAcrossTwoPhysicalTenants_recordsSeparatelyTaggedCounters() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ConnectorsInboundMetrics(registry);

    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-b"));

    var tenantACount =
        registry
            .find(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
            .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
            .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, "tenant-a")
            .counter()
            .count();
    var tenantBCount =
        registry
            .find(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
            .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
            .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, "tenant-b")
            .counter()
            .count();

    assertThat(tenantACount).isEqualTo(2.0);
    assertThat(tenantBCount).isEqualTo(1.0);
  }

  @Test
  void aggregatorInbound_withoutFilter_sumsAcrossAllPhysicalTenants() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ConnectorsInboundMetrics(registry);
    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-b"));

    var result = ConnectorMetricsAggregator.inbound(registry, "test-type", null, "runtime-1");

    assertThat(result.activation().activated()).isEqualTo(2);
  }

  @Test
  void aggregatorInbound_withFilter_scopesToMatchingPhysicalTenantsOnly() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ConnectorsInboundMetrics(registry);
    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-b"));

    var tenantAOnly =
        ConnectorMetricsAggregator.inbound(registry, "test-type", List.of("tenant-a"), "runtime-1");
    var bothTenants =
        ConnectorMetricsAggregator.inbound(
            registry, "test-type", List.of("tenant-a", "tenant-b"), "runtime-1");
    var unknownTenant =
        ConnectorMetricsAggregator.inbound(
            registry, "test-type", List.of("nonexistent-tenant"), "runtime-1");

    assertThat(tenantAOnly.activation().activated()).isEqualTo(2);
    assertThat(bothTenants.activation().activated()).isEqualTo(3);
    assertThat(unknownTenant.activation().activated()).isEqualTo(0);
  }

  @Test
  void aggregatorInbound_aggregateAcrossTypes_appliesFilterConsistently() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ConnectorsInboundMetrics(registry);
    metrics.increaseActivation(elementFor("tenant-a"));
    metrics.increaseActivation(elementFor("tenant-b"));

    // connectorType == null -> aggregate across all discovered types
    var unfiltered = ConnectorMetricsAggregator.inbound(registry, null, null, "runtime-1");
    var filtered =
        ConnectorMetricsAggregator.inbound(registry, null, List.of("tenant-a"), "runtime-1");

    assertThat(unfiltered.activation().activated()).isEqualTo(2);
    assertThat(filtered.activation().activated()).isEqualTo(1);
  }
}
