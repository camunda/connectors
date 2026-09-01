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
package io.camunda.connector.runtime.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCache;

class OutboundConnectorRuntimeConfigurationTest {

  private final OutboundConnectorRuntimeConfiguration configuration =
      new OutboundConnectorRuntimeConfiguration();

  @Test
  void secretKeyCacheStore_whenEnabled_returnsCaffeineCache() {
    var cache = configuration.secretKeyCacheStore(true, 1000);

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_returnsNoOpCache() {
    var cache = configuration.secretKeyCacheStore(false, 1000);

    assertInstanceOf(NoOpCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_cacheNeverStoresValues() {
    Cache cache = configuration.secretKeyCacheStore(false, 1000);

    var callCount = new AtomicInteger(0);
    cache.get("key", callCount::incrementAndGet);
    cache.get("key", callCount::incrementAndGet);

    assertEquals(2, callCount.get(), "NoOp cache must call loader on every get");
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsZero_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, 0);

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsNegative_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, -1);

    assertInstanceOf(CaffeineCache.class, cache);
  }

  private static CamundaClient clientWithPhysicalTenantId(String physicalTenantId) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn(physicalTenantId);
    return client;
  }

  @Test
  void documentStoresByPhysicalTenantId_usesExplicitlyConfiguredPhysicalTenantId() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("explicit-tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(clientA);

    var result = configuration.documentStoresByPhysicalTenantId(registry, null);

    assertThat(result).containsOnlyKeys("explicit-tenant");
  }

  @Test
  void documentStoresByPhysicalTenantId_fallsBackToClientNameWhenPhysicalTenantIdNotConfigured() {
    var registry = mock(CamundaClientRegistry.class);
    var clientB = clientWithPhysicalTenantId(null);
    when(registry.clientNames()).thenReturn(Set.of("engine-b"));
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = configuration.documentStoresByPhysicalTenantId(registry, null);

    assertThat(result).containsOnlyKeys("engine-b");
  }

  @Test
  void documentStoresByPhysicalTenantId_fallsBackToClientNameWhenConfigurationCannotBeRead() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-c"));
    var uninitializedClient = mock(CamundaClient.class);
    when(uninitializedClient.getConfiguration())
        .thenThrow(new RuntimeException("client not initialized"));
    when(registry.get("engine-c")).thenReturn(uninitializedClient);

    var result = configuration.documentStoresByPhysicalTenantId(registry, null);

    assertThat(result).containsOnlyKeys("engine-c");
  }

  @Test
  void documentStoresByPhysicalTenantId_fallsBackToLegacyCamundaClientWhenRegistryLookupFails() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default"))
        .thenThrow(
            new IllegalArgumentException("No CamundaClient configured under name 'default'"));
    var legacyClient = clientWithPhysicalTenantId("legacy-tenant");

    var result = configuration.documentStoresByPhysicalTenantId(registry, legacyClient);

    assertThat(result).containsOnlyKeys("legacy-tenant");
  }

  @Test
  void
      documentStoresByPhysicalTenantId_throwsClearErrorWhenNeitherRegistryNorLegacyClientIsAvailable() {
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("default"));
    when(registry.get("default"))
        .thenThrow(
            new IllegalArgumentException("No CamundaClient configured under name 'default'"));

    assertThatThrownBy(() -> configuration.documentStoresByPhysicalTenantId(registry, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("default");
  }

  @Test
  void
      documentStoresByPhysicalTenantId_throwsClearErrorWhenTwoClientsResolveToTheSamePhysicalTenantId() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("duplicate-tenant");
    var clientB = clientWithPhysicalTenantId("duplicate-tenant");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    assertThatThrownBy(() -> configuration.documentStoresByPhysicalTenantId(registry, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same physical tenant ID");
  }

  @Test
  void documentFactoriesByPhysicalTenantId_oneEntryPerConfiguredClient() {
    var registry = mock(CamundaClientRegistry.class);
    var clientA = clientWithPhysicalTenantId("tenant-a");
    var clientB = clientWithPhysicalTenantId("tenant-b");
    when(registry.clientNames()).thenReturn(Set.of("engine-a", "engine-b"));
    when(registry.get("engine-a")).thenReturn(clientA);
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = configuration.documentFactoriesByPhysicalTenantId(registry, null, null);

    assertThat(result).containsOnlyKeys("tenant-a", "tenant-b");
  }
}
