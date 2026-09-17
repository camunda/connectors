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

import static io.camunda.connector.runtime.TestCamundaClientProviders.clientProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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
    var cache = configuration.secretKeyCacheStore(true, 1000).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_returnsNoOpCache() {
    var cache = configuration.secretKeyCacheStore(false, 1000).cache();

    assertInstanceOf(NoOpCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenDisabled_cacheNeverStoresValues() {
    Cache cache = configuration.secretKeyCacheStore(false, 1000).cache();

    var callCount = new AtomicInteger(0);
    cache.get("key", callCount::incrementAndGet);
    cache.get("key", callCount::incrementAndGet);

    assertEquals(2, callCount.get(), "NoOp cache must call loader on every get");
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsZero_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, 0).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsNegative_clampedToDefault() {
    var cache = configuration.secretKeyCacheStore(true, -1).cache();

    assertInstanceOf(CaffeineCache.class, cache);
  }

  @Test
  void secretKeyCacheStore_beanTypeIsNotAPlainCache_soItCannotCollideWithAHostCacheBean() {
    // Regression: the bean previously returned a plain Cache, which collided with a host
    // application's own unqualified Cache bean the same way an unqualified CacheManager bean
    // used to. The holder type is what makes that impossible now, not the @Qualifier.
    var holder = configuration.secretKeyCacheStore(true, 1000);

    assertInstanceOf(SecretKeyCacheHolder.class, holder);
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

    var result = configuration.documentStoresByPhysicalTenantId(registry, clientProvider());

    assertThat(result).containsOnlyKeys("explicit-tenant");
  }

  @Test
  void documentStoresByPhysicalTenantId_fallsBackToClientNameWhenPhysicalTenantIdNotConfigured() {
    var registry = mock(CamundaClientRegistry.class);
    var clientB = clientWithPhysicalTenantId(null);
    when(registry.clientNames()).thenReturn(Set.of("engine-b"));
    when(registry.get("engine-b")).thenReturn(clientB);

    var result = configuration.documentStoresByPhysicalTenantId(registry, clientProvider());

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

    var result = configuration.documentStoresByPhysicalTenantId(registry, clientProvider());

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

    var result =
        configuration.documentStoresByPhysicalTenantId(registry, clientProvider(legacyClient));

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

    assertThatThrownBy(
            () -> configuration.documentStoresByPhysicalTenantId(registry, clientProvider()))
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

    assertThatThrownBy(
            () -> configuration.documentStoresByPhysicalTenantId(registry, clientProvider()))
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

    var result =
        configuration.documentFactoriesByPhysicalTenantId(registry, clientProvider(), null);

    assertThat(result).containsOnlyKeys("tenant-a", "tenant-b");
  }

  private record ObjectTypedProperty(Object value) {}

  private static ObjectMapper buildOutboundConnectorObjectMapper(DocumentFactory documentFactory)
      throws Exception {
    Method method =
        OutboundConnectorRuntimeConfiguration.class.getDeclaredMethod(
            "buildOutboundConnectorObjectMapper", DocumentFactory.class);
    method.setAccessible(true);
    return (ObjectMapper) method.invoke(null, documentFactory);
  }

  private static Map<String, Object> exploitNode() {
    // The issue's PoC payload: createLink against an arbitrary document reference, arriving as
    // ordinary Object-typed connector-property data (e.g. an ioMapping expression's result).
    return Map.of(
        "camunda.function.type",
        "createLink",
        "params",
        List.of(Map.of("camunda.document.type", "camunda"), "PT1H"));
  }

  @Test
  void
      buildOutboundConnectorObjectMapper_refusesIntrinsicFunctionDispatchThroughAnObjectTypedProperty()
          throws Exception {
    // Regression coverage for security-testing-findings#275, on the actual production
    // per-physical-tenant mapper construction path — the "equivalent surface" duplicating
    // ConnectorsAutoConfiguration's own outbound mapper builder.
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(mock(DocumentFactory.class));
    var payload = Map.of("value", exploitNode());

    assertThatThrownBy(() -> mapper.convertValue(payload, ObjectTypedProperty.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Intrinsic function dispatch is disabled");
  }

  @Test
  void buildOutboundConnectorObjectMapper_aDocumentReferenceStillMaterializes() throws Exception {
    var documentFactory = mock(DocumentFactory.class);
    var expectedDocument = mock(Document.class);
    when(documentFactory.resolve(any())).thenReturn(expectedDocument);
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(documentFactory);

    var payload =
        Map.of(
            "value",
            Map.of(
                "camunda.document.type", "camunda",
                "storeId", "store-1",
                "documentId", "doc-1",
                "contentHash", "hash-1"));

    var result = mapper.convertValue(payload, ObjectTypedProperty.class);

    assertThat(result.value()).isEqualTo(expectedDocument);
  }

  @Test
  void buildOutboundConnectorObjectMapper_ordinaryDataStillBinds() throws Exception {
    ObjectMapper mapper = buildOutboundConnectorObjectMapper(mock(DocumentFactory.class));

    var result = mapper.convertValue(Map.of("value", "hello"), ObjectTypedProperty.class);

    assertThat(result.value()).isEqualTo("hello");
  }
}
