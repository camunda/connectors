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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A {@link Cache} that never stores anything: {@link #get} always invokes the mapping function and
 * discards the result. Backs the "disabled" state of {@link SecretKeyCacheHolder}. Caffeine has no
 * built-in equivalent to Spring's {@code NoOpCache} this replaces: a {@code maximumSize(0)} cache
 * does not give the same guarantee, since its eviction runs on Caffeine's own maintenance cycle
 * rather than synchronously on every write, so a value can still be observed on an
 * immediately-following get.
 */
final class NoOpCache<K, V> implements Cache<K, V> {

  @Override
  public V getIfPresent(K key) {
    return null;
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> mappingFunction) {
    return mappingFunction.apply(key);
  }

  @Override
  public Map<K, V> getAllPresent(Iterable<? extends K> keys) {
    return Map.of();
  }

  @Override
  public Map<K, V> getAll(
      Iterable<? extends K> keys,
      Function<? super Set<? extends K>, ? extends Map<? extends K, ? extends V>> mappingFunction) {
    return Map.of();
  }

  @Override
  public void put(K key, V value) {}

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {}

  @Override
  public void invalidate(K key) {}

  @Override
  public void invalidateAll(Iterable<? extends K> keys) {}

  @Override
  public void invalidateAll() {}

  @Override
  public long estimatedSize() {
    return 0;
  }

  @Override
  public CacheStats stats() {
    return CacheStats.empty();
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    return new ConcurrentHashMap<>();
  }

  @Override
  public void cleanUp() {}

  @Override
  public Policy<K, V> policy() {
    throw new UnsupportedOperationException("NoOpCache has no eviction policy");
  }
}
