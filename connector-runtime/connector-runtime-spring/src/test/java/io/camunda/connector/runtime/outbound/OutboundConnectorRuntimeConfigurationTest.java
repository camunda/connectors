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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutboundConnectorRuntimeConfigurationTest {

  private final OutboundConnectorRuntimeConfiguration configuration =
      new OutboundConnectorRuntimeConfiguration();

  @Test
  void secretKeyCacheStore_whenEnabled_cachesAValueAcrossGets() {
    Cache<Long, String> cache = rawCache(configuration.secretKeyCacheStore(true, 1000));

    var callCount = new AtomicInteger(0);
    cache.get(1L, k -> "v" + callCount.incrementAndGet());
    cache.get(1L, k -> "v" + callCount.incrementAndGet());

    assertEquals(1, callCount.get(), "an enabled cache must memoize across gets for the same key");
  }

  @Test
  void secretKeyCacheStore_whenDisabled_cacheNeverStoresValues() {
    Cache<Long, String> cache = rawCache(configuration.secretKeyCacheStore(false, 1000));

    var callCount = new AtomicInteger(0);
    cache.get(1L, k -> "v" + callCount.incrementAndGet());
    cache.get(1L, k -> "v" + callCount.incrementAndGet());

    assertEquals(2, callCount.get(), "a disabled cache must call the loader on every get");
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsZero_clampedToDefault() {
    Cache<Long, String> cache = rawCache(configuration.secretKeyCacheStore(true, 0));

    var callCount = new AtomicInteger(0);
    cache.get(1L, k -> "v" + callCount.incrementAndGet());
    cache.get(1L, k -> "v" + callCount.incrementAndGet());

    assertEquals(
        1, callCount.get(), "clamped-to-default must still memoize, unlike the disabled case");
  }

  @Test
  void secretKeyCacheStore_whenMaxSizeIsNegative_clampedToDefault() {
    Cache<Long, String> cache = rawCache(configuration.secretKeyCacheStore(true, -1));

    var callCount = new AtomicInteger(0);
    cache.get(1L, k -> "v" + callCount.incrementAndGet());
    cache.get(1L, k -> "v" + callCount.incrementAndGet());

    assertEquals(
        1, callCount.get(), "clamped-to-default must still memoize, unlike the disabled case");
  }

  @Test
  void secretKeyCacheStore_beanTypeIsNotAPlainCache_soItCannotCollideWithAHostCacheBean() {
    // Regression: the bean previously returned a plain Spring Cache (backed by
    // spring-context-support), which collided with a host application's own unqualified Cache
    // bean the same way an unqualified CacheManager bean used to -- and separately put
    // JCacheCacheManager on the classpath, which this runtime's own transitive cache-api
    // dependency (via the Operate client) made eligible for Spring Boot's JCache
    // auto-configuration, able to silently replace a host's own auto-configured CacheManager. The
    // holder type wrapping Caffeine's own Cache is what makes both impossible now.
    var holder = configuration.secretKeyCacheStore(true, 1000);

    assertInstanceOf(SecretKeyCacheHolder.class, holder);
    assertThat(holder.cache()).isInstanceOf(Cache.class);
  }

  @SuppressWarnings("unchecked")
  private static Cache<Long, String> rawCache(SecretKeyCacheHolder holder) {
    return (Cache<Long, String>) (Cache<?, ?>) holder.cache();
  }

  @Test
  void secretKeyCacheAndIntrinsicFunctionAllowListCache_shareOneBpmnModelCacheStore_fetchOnce()
      throws Exception {
    // Regression for the intrinsic-function allow-list cache sharing its BPMN-model fetch with
    // the pre-existing secret-key cache: before this, each would have called
    // CamundaOperateClient#getProcessDefinitionModel independently for the same process
    // definition. Both beans now build their ProcessDefinitionModelCache from the same
    // bpmnModelCacheStore, so the first consumer to ask for a given process definition key is the
    // only one that ever calls out to Operate for it.
    var camundaOperateClient = mock(CamundaOperateClient.class);
    when(camundaOperateClient.getProcessDefinitionModel(anyLong()))
        .thenReturn(loadBpmn("outbound-with-secrets.bpmn"));
    var bpmnModelCacheStore = configuration.bpmnModelCacheStore(true, 1000);

    var secretKeyCache =
        configuration.secretKeyCache(
            camundaOperateClient,
            configuration.secretKeyCacheStore(true, 1000),
            bpmnModelCacheStore);
    var intrinsicFunctionAllowListCache =
        configuration.intrinsicFunctionAllowListCache(
            camundaOperateClient,
            bpmnModelCacheStore,
            configuration.intrinsicFunctionAllowListCacheStore(true, 1000));
    var deadline = Instant.now().plusSeconds(30);

    secretKeyCache.getSecretKeys(new SecretKeyContext(42L, "service-task-1", deadline));
    intrinsicFunctionAllowListCache.getAllowedFunctions(
        new IntrinsicFunctionAllowListContext(42L, "service-task-1", deadline));

    verify(camundaOperateClient, times(1)).getProcessDefinitionModel(42L);
  }

  private static io.camunda.zeebe.model.bpmn.BpmnModelInstance loadBpmn(String fileName)
      throws Exception {
    try (var stream =
        OutboundConnectorRuntimeConfigurationTest.class
            .getClassLoader()
            .getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return Bpmn.readModelFromStream(stream);
    }
  }
}
