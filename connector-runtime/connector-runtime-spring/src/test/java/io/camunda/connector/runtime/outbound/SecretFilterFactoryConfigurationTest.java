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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class SecretFilterFactoryConfigurationTest {

  private final SecretFilterFactoryConfiguration configuration =
      new SecretFilterFactoryConfiguration();

  @Test
  void secretKeyCacheManager_whenEnabled_returnsCaffeineCacheManager() {
    var cacheManager = configuration.secretKeyCacheManager(true, 1000);

    assertInstanceOf(CaffeineCacheManager.class, cacheManager);
  }

  @Test
  void secretKeyCacheManager_whenDisabled_returnsNoOpCacheManager() {
    var cacheManager = configuration.secretKeyCacheManager(false, 1000);

    assertInstanceOf(NoOpCacheManager.class, cacheManager);
  }

  @Test
  void secretKeyCacheManager_whenDisabled_cacheNeverStoresValues() throws Exception {
    var cacheManager = configuration.secretKeyCacheManager(false, 1000);
    Cache cache = cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME);

    var callCount = new AtomicInteger(0);
    cache.get("key", callCount::incrementAndGet);
    cache.get("key", callCount::incrementAndGet);

    assertEquals(2, callCount.get(), "NoOp cache must call loader on every get");
  }

  @Test
  void secretKeyCacheManager_whenMaxSizeIsZero_clampedToDefault() {
    var cacheManager = configuration.secretKeyCacheManager(true, 0);

    assertInstanceOf(CaffeineCacheManager.class, cacheManager);
  }

  @Test
  void secretKeyCacheManager_whenMaxSizeIsNegative_clampedToDefault() {
    var cacheManager = configuration.secretKeyCacheManager(true, -1);

    assertInstanceOf(CaffeineCacheManager.class, cacheManager);
  }
}
