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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises the physical-tenant-id resolution/fallback logic in {@link PhysicalTenantIds} via
 * {@link InboundConnectorRuntimeConfiguration}'s {@code searchQueryClientRegistry} bean method (a
 * plain, non-Spring-context call), which routes through {@code resolveClient}, {@code
 * resolvePhysicalTenantId} and {@code buildSearchQueryClientRegistrationsByPhysicalTenantId}.
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

    var result = configuration.searchQueryClientRegistry(registry, null, null, 200).snapshot();

    assertThat(result).containsOnlyKeys("explicit-tenant");
  }

  @Test
  void fallsBackToClientNameWhenPhysicalTenantIdNotConfigured() {
    var registry = mock(CamundaClientRegistry.class);
    var clientB = clientWithPhysicalTenantId(null);
    when(registry.clientNames()).thenReturn(Set.of("engine-b"));
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = configuration.searchQueryClientRegistry(registry, null, null, 200).snapshot();

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
        .thenThrow(new RuntimeException("client not initialized"))
        .thenReturn(clientWithPhysicalTenantId("resolved-tenant").getConfiguration());
    when(registry.get("engine-c")).thenReturn(uninitializedClient);

    var searchQueryClientRegistry =
        configuration.searchQueryClientRegistry(registry, null, null, 200);

    assertThat(searchQueryClientRegistry.snapshot()).containsOnlyKeys("engine-c");

    searchQueryClientRegistry.onStart(uninitializedClient, "engine-c");

    assertThat(searchQueryClientRegistry.snapshot()).containsOnlyKeys("resolved-tenant");
  }

  @Test
  void getResolvesByOldClientNameAfterFallbackMigration() {
    // a caller that resolved and captured "engine-c" (e.g. a per-physical-tenant map built once,
    // before the client's real configuration became readable) must keep working after onStart
    // migrates the registration to "resolved-tenant" — the map key changes underneath it, but the
    // registration's clientName does not.
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-c"));
    var uninitializedClient = mock(CamundaClient.class);
    when(uninitializedClient.getConfiguration())
        .thenThrow(new RuntimeException("client not initialized"))
        .thenReturn(clientWithPhysicalTenantId("resolved-tenant").getConfiguration());
    when(registry.get("engine-c")).thenReturn(uninitializedClient);
    var searchQueryClientRegistry =
        configuration.searchQueryClientRegistry(registry, null, null, 200);

    searchQueryClientRegistry.onStart(uninitializedClient, "engine-c");

    assertThat(searchQueryClientRegistry.get("engine-c"))
        .isSameAs(searchQueryClientRegistry.get("resolved-tenant"));
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
        configuration.searchQueryClientRegistry(registry, legacyClient, null, 200).snapshot();

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

    var searchQueryClientRegistry =
        configuration.searchQueryClientRegistry(registry, null, overrideSearchQueryClient, 200);

    searchQueryClientRegistry.onStart(client, "default");

    assertThat(searchQueryClientRegistry.snapshot())
        .containsOnly(Map.entry("tenant", overrideSearchQueryClient));
  }

  @Test
  void lifecycleDeclinesDuplicatePhysicalTenantBeforeInitialClientStartEventWithoutThrowing() {
    // Thrown here would abort CamundaClientEventListener's unguarded forEach over every
    // CamundaClientLifecycleAware bean (and, upstream, the multi-client producer's forEach over
    // every configured client), taking down clients processed after this one for an unrelated
    // misconfiguration. So a runtime duplicate is declined instead of thrown.
    var registry = mock(CamundaClientRegistry.class);
    var initialClient = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(initialClient);
    var searchQueryClientRegistry =
        configuration.searchQueryClientRegistry(registry, null, null, 200);
    var duplicateClient = clientWithPhysicalTenantId("tenant");

    assertThatCode(() -> searchQueryClientRegistry.onStart(duplicateClient, "engine-b"))
        .doesNotThrowAnyException();

    // "engine-a" keeps the registration: onStop for it still finds and removes it, which would
    // not be the case had "engine-b" silently overwritten the mapping.
    searchQueryClientRegistry.onStop(initialClient, "engine-a");
    assertThatThrownBy(() -> searchQueryClientRegistry.get("tenant"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsClearErrorWhenNeitherRegistryNorLegacyClientIsAvailable() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default"))
        .thenThrow(
            new IllegalArgumentException("No CamundaClient configured under name 'default'"));

    assertThatThrownBy(() -> configuration.searchQueryClientRegistry(registry, null, null, 200))
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

    assertThatThrownBy(() -> configuration.searchQueryClientRegistry(registry, null, null, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same physical tenant ID");
  }

  @Test
  void buildDocumentFactoriesByPhysicalTenantId_usesManuallySuppliedOverrideForASingleClient() {
    // Simulates an application supplying its own primary DocumentFactory.
    var registry = mock(CamundaClientRegistry.class);
    var client = clientWithPhysicalTenantId("tenant");
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default")).thenReturn(client);
    var overrideDocumentFactory = mock(DocumentFactory.class);

    var result =
        PhysicalTenantIds.buildDocumentFactoriesByPhysicalTenantId(
            registry, null, overrideDocumentFactory);

    assertThat(result).containsOnly(Map.entry("tenant", overrideDocumentFactory));
  }

  @Test
  void buildDocumentFactoriesByPhysicalTenantId_buildsOneRealFactoryPerPhysicalTenant() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("tenant-a");
    var clientB = clientWithPhysicalTenantId("tenant-b");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = PhysicalTenantIds.buildDocumentFactoriesByPhysicalTenantId(registry, null, null);

    assertThat(result).containsOnlyKeys("tenant-a", "tenant-b");
    assertThat(result.get("tenant-a")).isNotSameAs(result.get("tenant-b"));
  }

  @Test
  void buildDocumentFactoriesByPhysicalTenantId_wiresEachFactoryToItsOwnResolvedPhysicalTenantId() {
    // each factory's underlying CamundaDocumentStoreImpl must know ITS OWN physical tenant ID
    // (not just be backed by the right client), so it can reject a request explicitly addressed
    // to a different physical tenant (see CamundaDocumentStoreImplTest)
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("tenant-a");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(clientA);

    var result = PhysicalTenantIds.buildDocumentFactoriesByPhysicalTenantId(registry, null, null);

    var request =
        DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes()))
            .physicalTenantId("tenant-b")
            .build();
    assertThatThrownBy(() -> result.get("tenant-a").create(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant-a")
        .hasMessageContaining("tenant-b");
  }
}
