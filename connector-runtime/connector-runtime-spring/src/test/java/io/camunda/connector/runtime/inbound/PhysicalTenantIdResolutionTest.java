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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises the physical-tenant-id resolution/fallback logic in {@link PhysicalTenantIds} via
 * {@link InboundConnectorRuntimeConfiguration}'s {@code searchQueryClientsByPhysicalTenantId} bean
 * method (a plain, non-Spring-context call), which routes through {@code resolveClient}, {@code
 * resolvePhysicalTenantId} and {@code toMapByPhysicalTenantId}.
 */
class PhysicalTenantIdResolutionTest {

  private final InboundConnectorRuntimeConfiguration configuration =
      new InboundConnectorRuntimeConfiguration();

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  @Test
  void usesExplicitlyConfiguredPhysicalTenantId() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("explicit-tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(clientA);

    var result = configuration.searchQueryClientsByPhysicalTenantId(registry, null, null, 200);

    assertThat(result).containsOnlyKeys("explicit-tenant");
  }

  @Test
  void fallsBackToClientNameWhenPhysicalTenantIdNotConfigured() {
    var registry = mock(CamundaClientRegistry.class);
    var clientB = clientWithPhysicalTenantId(null);
    when(registry.clientNames()).thenReturn(Set.of("engine-b"));
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = configuration.searchQueryClientsByPhysicalTenantId(registry, null, null, 200);

    assertThat(result).containsOnlyKeys("engine-b");
  }

  @Test
  void fallsBackToClientNameWhenConfigurationCannotBeRead() {
    // simulates a test-proxy CamundaClient (e.g. camunda-process-test-spring) that throws when
    // queried before the real test container is ready
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-c"));
    var uninitializedClient = mock(CamundaClient.class);
    when(uninitializedClient.getConfiguration())
        .thenThrow(new RuntimeException("client not initialized"));
    when(registry.get("engine-c")).thenReturn(uninitializedClient);

    var result = configuration.searchQueryClientsByPhysicalTenantId(registry, null, null, 200);

    assertThat(result).containsOnlyKeys("engine-c");
  }

  @Test
  void fallsBackToLegacyCamundaClientWhenRegistryLookupFails() {
    // simulates a manually-supplied CamundaClient bean (e.g. this repo's own @MockitoBean test
    // pattern) that bypasses the registry's own client-bean registration
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default"))
        .thenThrow(
            new IllegalArgumentException("No CamundaClient configured under name 'default'"));
    var legacyClient = clientWithPhysicalTenantId("legacy-tenant");

    var result =
        configuration.searchQueryClientsByPhysicalTenantId(registry, legacyClient, null, 200);

    assertThat(result).containsOnlyKeys("legacy-tenant");
  }

  @Test
  void usesManuallySuppliedSearchQueryClientOverrideInsteadOfConstructingARealOne() {
    // simulates the @MockitoBean SearchQueryClient pattern used across several single-client E2E
    // test suites (e.g. HttpTests, BaseRabbitMqTest) to control process-definition search results
    var registry = mock(CamundaClientRegistry.class);
    var client = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default")).thenReturn(client);
    var overrideSearchQueryClient = mock(SearchQueryClient.class);

    var result =
        configuration.searchQueryClientsByPhysicalTenantId(
            registry, null, overrideSearchQueryClient, 200);

    assertThat(result).containsOnly(Map.entry("tenant", overrideSearchQueryClient));
  }

  @Test
  void throwsClearErrorWhenNeitherRegistryNorLegacyClientIsAvailable() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default"))
        .thenThrow(
            new IllegalArgumentException("No CamundaClient configured under name 'default'"));

    assertThatThrownBy(
            () -> configuration.searchQueryClientsByPhysicalTenantId(registry, null, null, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("default");
  }

  @Test
  void throwsClearErrorWhenTwoClientsResolveToTheSamePhysicalTenantId() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("duplicate-tenant");
    var clientB = clientWithPhysicalTenantId("duplicate-tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    assertThatThrownBy(
            () -> configuration.searchQueryClientsByPhysicalTenantId(registry, null, null, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same physical tenant ID");
  }
}
