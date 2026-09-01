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

import com.github.benmanes.caffeine.cache.Cache;
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
}
