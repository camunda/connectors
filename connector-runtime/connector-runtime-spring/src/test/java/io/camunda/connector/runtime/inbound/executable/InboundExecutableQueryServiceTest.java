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
package io.camunda.connector.runtime.inbound.executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.InvalidInboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that health timestamps are stable across multiple queries for non-Activated states.
 *
 * <p>Bug: {@link InboundExecutableQueryService} was creating new {@link Health} objects on every
 * call to {@code query()}, causing {@code health.getLastUpdatedAt()} to always return the current
 * time instead of the time the failure occurred.
 */
@ExtendWith(MockitoExtension.class)
class InboundExecutableQueryServiceTest {

  @Mock private InboundConnectorFactory connectorFactory;
  @Mock private InboundConnectorExecutable<?> executable;
  @Mock private InboundConnectorManagementContext context;

  private InMemoryInboundExecutableStateStore stateStore;
  private InboundExecutableQueryService queryService;

  @BeforeEach
  void setUp() {
    // lenient: only used by getConnectorName(), not by the query tests below
    lenient().when(connectorFactory.getActiveConfigurations()).thenReturn(List.of());
    stateStore = new InMemoryInboundExecutableStateStore();
    queryService =
        new InboundExecutableQueryService(stateStore, connectorFactory, new ActivityLogRegistry());
  }

  private InboundConnectorElement testElement() {
    return new InboundConnectorElement(
        Map.of("inbound.type", "test-type"),
        new StartEventCorrelationPoint("processId", 0, 0),
        new ProcessElementWithRuntimeData("processId", 0, 0, "elementId", "tenant"));
  }

  private ValidInboundConnectorDetails testValidDetails(String dedupId) {
    return new ValidInboundConnectorDetails(
        "test-type", "tenant", dedupId, Map.of(), List.of(testElement()), "processId");
  }

  private InvalidInboundConnectorDetails testInvalidDetails(String dedupId) {
    return new InvalidInboundConnectorDetails(
        List.of(testElement()),
        new RuntimeException("invalid config"),
        "tenant",
        dedupId,
        "test-type",
        "processId");
  }

  /**
   * Demonstrates the bug: {@code FailedToActivate} health timestamp changes on every query because
   * a new {@link Health} object is created each time in {@link InboundExecutableQueryService}.
   *
   * <p>After the fix, the {@link Health} is stored in the record at creation time and reused.
   */
  @Test
  void failedToActivate_healthLastUpdatedAt_shouldBeStableAcrossQueries()
      throws InterruptedException {
    var id = ExecutableId.fromDeduplicationId("test-failed");
    stateStore.put(
        id,
        new RegisteredExecutable.FailedToActivate(
            testValidDetails("test-failed"),
            "activation failed",
            id,
            Health.down(new RuntimeException("activation failed"))));

    var health1 = queryService.query(null).get(0).health();
    Thread.sleep(5); // ensure clock advances between the two calls
    var health2 = queryService.query(null).get(0).health();

    // Health.lastUpdatedAt must not change between queries — it should reflect
    // when the failure was recorded, not when the API was called.
    assertThat(health1.getLastUpdatedAt())
        .as("lastUpdatedAt should be stable across queries for FailedToActivate")
        .isEqualTo(health2.getLastUpdatedAt());
  }

  @Test
  void invalidDefinition_healthLastUpdatedAt_shouldBeStableAcrossQueries()
      throws InterruptedException {
    var id = ExecutableId.fromDeduplicationId("test-invalid");
    var invalid = testInvalidDetails("test-invalid");
    stateStore.put(
        id,
        new RegisteredExecutable.InvalidDefinition(
            invalid,
            "Invalid connector definition: invalid config",
            id,
            Health.down(new RuntimeException("Invalid connector definition: invalid config"))));

    var health1 = queryService.query(null).get(0).health();
    Thread.sleep(5);
    var health2 = queryService.query(null).get(0).health();

    assertThat(health1.getLastUpdatedAt())
        .as("lastUpdatedAt should be stable across queries for InvalidDefinition")
        .isEqualTo(health2.getLastUpdatedAt());
  }

  @Test
  void connectorNotRegistered_healthLastUpdatedAt_shouldBeStableAcrossQueries()
      throws InterruptedException {
    var id = ExecutableId.fromDeduplicationId("test-not-registered");
    stateStore.put(
        id,
        new RegisteredExecutable.ConnectorNotRegistered(
            testValidDetails("test-not-registered"),
            id,
            Health.down(new RuntimeException("Connector test-type not registered"))));

    var health1 = queryService.query(null).get(0).health();
    Thread.sleep(5);
    var health2 = queryService.query(null).get(0).health();

    assertThat(health1.getLastUpdatedAt())
        .as("lastUpdatedAt should be stable across queries for ConnectorNotRegistered")
        .isEqualTo(health2.getLastUpdatedAt());
  }

  @Test
  @SuppressWarnings("unchecked")
  void cancelled_healthLastUpdatedAt_shouldBeStableAcrossQueries() throws InterruptedException {
    var id = ExecutableId.fromDeduplicationId("test-cancelled");
    var throwable = new RuntimeException("connector timed out");
    // Simulate what cancelExecutable() does: report health on the context once, then read it back.
    // The same Health object is returned on every call — timestamp is stable.
    var stableHealth = Health.down(throwable);
    when(context.connectorElements()).thenReturn(List.of(testElement()));
    when(context.getActivationTimestamp()).thenReturn(0L);
    when(context.getHealth()).thenReturn(stableHealth);
    stateStore.put(
        id,
        new RegisteredExecutable.Cancelled(
            (InboundConnectorExecutable<io.camunda.connector.api.inbound.InboundConnectorContext>)
                executable,
            context,
            throwable,
            id));

    var health1 = queryService.query(null).get(0).health();
    Thread.sleep(5); // ensure clock advances between the two calls
    var health2 = queryService.query(null).get(0).health();

    assertThat(health1.getLastUpdatedAt())
        .as("lastUpdatedAt should be stable across queries for Cancelled")
        .isEqualTo(health2.getLastUpdatedAt());
  }

  @Test
  void aggregateHealth_withDownExecutable_returnsDownWithPerExecutableDetails() {
    var id = ExecutableId.fromDeduplicationId("test-down");
    var error = new Health.Error("SOME_ERROR", "something failed");
    stateStore.put(
        id,
        new RegisteredExecutable.FailedToActivate(
            testValidDetails("test-down"), "activation failed", id, Health.down(error)));

    var result = queryService.aggregateHealth();

    assertThat(result.getStatus()).isEqualTo(Health.Status.DOWN);
    assertThat(result.getError().code()).isEqualTo("CONNECTORS_DOWN");
    assertThat(result.getDetails()).containsKey(id.getId());
    var executableHealth = (Health) result.getDetails().get(id.getId());
    assertThat(executableHealth.getStatus()).isEqualTo(Health.Status.DOWN);
    assertThat(executableHealth.getError()).isEqualTo(error);
  }

  @Test
  void aggregateHealth_downExecutableDetails_containEnrichmentKeys() {
    var id = ExecutableId.fromDeduplicationId("test-down");
    stateStore.put(
        id,
        new RegisteredExecutable.FailedToActivate(
            testValidDetails("test-down"),
            "activation failed",
            id,
            Health.down(new Health.Error("ERROR", "failed"))));

    var result = queryService.aggregateHealth();

    var executableHealth = (Health) result.getDetails().get(id.getId());
    assertThat(executableHealth.getDetails())
        .containsEntry("processId", "processId")
        .containsEntry("tenantId", "tenant")
        .containsEntry("type", "test-type");
  }

  @Test
  void aggregateHealth_connectorProvidedDetailsNotOverwrittenByEnrichment() {
    var id = ExecutableId.fromDeduplicationId("test-down");
    var connectorHealth =
        Health.down(
            new Health.Error("ERROR", "failed"),
            Map.of("type", "connector-provided-type", "customKey", "customValue"));
    stateStore.put(
        id,
        new RegisteredExecutable.FailedToActivate(
            testValidDetails("test-down"), "activation failed", id, connectorHealth));

    var result = queryService.aggregateHealth();

    var executableHealth = (Health) result.getDetails().get(id.getId());
    assertThat(executableHealth.getDetails())
        .containsEntry("type", "connector-provided-type")
        .containsEntry("customKey", "customValue");
  }

  @Test
  void aggregateHealth_enrichedHealth_preservesLastUpdatedAt() throws InterruptedException {
    var id = ExecutableId.fromDeduplicationId("test-down");
    var originalHealth = Health.down(new Health.Error("ERROR", "failed"));
    var originalTimestamp = originalHealth.getLastUpdatedAt();
    stateStore.put(
        id,
        new RegisteredExecutable.FailedToActivate(
            testValidDetails("test-down"), "activation failed", id, originalHealth));

    Thread.sleep(5);
    var result = queryService.aggregateHealth();

    var executableHealth = (Health) result.getDetails().get(id.getId());
    assertThat(executableHealth.getLastUpdatedAt())
        .as("lastUpdatedAt should be preserved after enrichment, not reset to query time")
        .isEqualTo(originalTimestamp);
  }
}
