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
package io.camunda.connector.runtime.inbound.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.FileInputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.util.ResourceUtils;

class ProcessDefinitionInspectorCacheMetricsTest {

  private static final ProcessDefinitionRef PROCESS_REF =
      new ProcessDefinitionRef("process", "tenant1");
  private static final long PROCESS_DEFINITION_KEY = 1L;

  private SearchQueryClient searchQueryClient;
  private SimpleMeterRegistry meterRegistry;
  private ConnectorsInboundMetrics metrics;

  @BeforeEach
  void setUp() throws Exception {
    searchQueryClient = mock(SearchQueryClient.class);
    BpmnModelInstance model;
    try (FileInputStream inputStream =
        new FileInputStream(
            ResourceUtils.getFile("classpath:bpmn/single-webhook-collaboration.bpmn"))) {
      model = Bpmn.readModelFromStream(inputStream);
    }
    when(searchQueryClient.getProcessModel(PROCESS_DEFINITION_KEY)).thenReturn(model);
    var processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getVersion()).thenReturn(1);
    when(searchQueryClient.getProcessDefinition(PROCESS_DEFINITION_KEY))
        .thenReturn(processDefinition);

    meterRegistry = new SimpleMeterRegistry();
    metrics = new ConnectorsInboundMetrics(meterRegistry);
  }

  @Test
  void recordsMissThenHitForRepeatedLookupsOfSameKey() {
    var inspector =
        new ProcessDefinitionInspector(
            Map.of(ProcessDefinitionRef.DEFAULT_PHYSICAL_TENANT_ID, searchQueryClient),
            caffeineCache(),
            metrics);

    inspector.findInboundConnectors(PROCESS_REF, PROCESS_DEFINITION_KEY);
    inspector.findInboundConnectors(PROCESS_REF, PROCESS_DEFINITION_KEY);
    inspector.findInboundConnectors(PROCESS_REF, PROCESS_DEFINITION_KEY);

    assertThat(cacheAccessCount(ConnectorMetrics.Inbound.RESULT_CACHE_MISS)).isEqualTo(1.0);
    assertThat(cacheAccessCount(ConnectorMetrics.Inbound.RESULT_CACHE_HIT)).isEqualTo(2.0);
    // BPMN model is only fetched on the miss, subsequent lookups are served from cache.
    verify(searchQueryClient, times(1)).getProcessModel(PROCESS_DEFINITION_KEY);
  }

  @Test
  void doesNotConflateResultsAcrossPhysicalTenantsWithCollidingProcessDefinitionKey()
      throws Exception {
    // given - two physical tenants (independent Zeebe clusters), each happening to assign the
    // SAME raw processDefinitionKey to their own, different, deployed process.
    var clientA = mock(SearchQueryClient.class);
    var clientB = mock(SearchQueryClient.class);

    stubModel(clientA, "single-webhook-collaboration.bpmn");
    stubModel(clientB, "single-webhook-boundary.bpmn");

    var inspector =
        new ProcessDefinitionInspector(
            Map.of("physical-tenant-a", clientA, "physical-tenant-b", clientB),
            caffeineCache(),
            metrics);

    var refA = new ProcessDefinitionRef("physical-tenant-a", "process", "tenant1");
    var refB = new ProcessDefinitionRef("physical-tenant-b", "BoundaryEventTest", "tenant1");

    // when - both physical tenants are queried with the same colliding processDefinitionKey
    var elementsA = inspector.findInboundConnectors(refA, PROCESS_DEFINITION_KEY);
    var elementsB = inspector.findInboundConnectors(refB, PROCESS_DEFINITION_KEY);

    // then - each physical tenant's own model was parsed, not conflated with the other's
    assertThat(elementsA).extracting(e -> e.element().elementId()).containsExactly("start_event");
    assertThat(elementsB)
        .extracting(e -> e.element().elementId())
        .containsExactly("boundary_event");
    verify(clientA, times(1)).getProcessModel(PROCESS_DEFINITION_KEY);
    verify(clientB, times(1)).getProcessModel(PROCESS_DEFINITION_KEY);

    // and - repeated lookups are served from cache without conflating the two tenants
    var elementsA2 = inspector.findInboundConnectors(refA, PROCESS_DEFINITION_KEY);
    var elementsB2 = inspector.findInboundConnectors(refB, PROCESS_DEFINITION_KEY);
    assertThat(elementsA2).extracting(e -> e.element().elementId()).containsExactly("start_event");
    assertThat(elementsB2)
        .extracting(e -> e.element().elementId())
        .containsExactly("boundary_event");
    // still only 1 call each — the second lookup was a cache hit, not a re-fetch
    verify(clientA, times(1)).getProcessModel(PROCESS_DEFINITION_KEY);
    verify(clientB, times(1)).getProcessModel(PROCESS_DEFINITION_KEY);
  }

  private void stubModel(SearchQueryClient client, String bpmnResource) throws Exception {
    BpmnModelInstance model;
    try (FileInputStream inputStream =
        new FileInputStream(ResourceUtils.getFile("classpath:bpmn/" + bpmnResource))) {
      model = Bpmn.readModelFromStream(inputStream);
    }
    when(client.getProcessModel(PROCESS_DEFINITION_KEY)).thenReturn(model);
    var processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getVersion()).thenReturn(1);
    when(client.getProcessDefinition(PROCESS_DEFINITION_KEY)).thenReturn(processDefinition);
  }

  @Test
  void countsEveryLookupAsMissWhenCachingIsDisabled() {
    Cache noOpCache =
        new NoOpCacheManager().getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME);
    var inspector =
        new ProcessDefinitionInspector(
            Map.of(ProcessDefinitionRef.DEFAULT_PHYSICAL_TENANT_ID, searchQueryClient),
            noOpCache,
            metrics);

    inspector.findInboundConnectors(PROCESS_REF, PROCESS_DEFINITION_KEY);
    inspector.findInboundConnectors(PROCESS_REF, PROCESS_DEFINITION_KEY);

    assertThat(cacheAccessCount(ConnectorMetrics.Inbound.RESULT_CACHE_MISS)).isEqualTo(2.0);
    assertThat(cacheAccessCount(ConnectorMetrics.Inbound.RESULT_CACHE_HIT)).isEqualTo(0.0);
  }

  private Cache caffeineCache() {
    return new CaffeineCacheManager(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME)
        .getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME);
  }

  private double cacheAccessCount(String result) {
    return meterRegistry
        .get(ConnectorMetrics.Inbound.METRIC_NAME_PROCESS_DEFINITION_CACHE_ACCESSES)
        .tag(ConnectorMetrics.Tag.RESULT, result)
        .counter()
        .count();
  }
}
