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
package io.camunda.connector.runtime.managedcode;

import io.camunda.client.CamundaClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches script and manifest resources by key. Resource content is immutable for a resource key,
 * so it is kept in a bounded, least-recently-used cache.
 */
final class ScriptResources {

  @FunctionalInterface
  interface Fetcher {
    ScriptResource fetch(CamundaClient client, long resourceKey);
  }

  /**
   * @param content UTF-8 bytes of the content returned by Camunda; the same array is hashed into
   *     the artifact digest and handed to the provider
   */
  record ScriptResource(String name, byte[] content) {}

  private record CacheKey(String physicalTenantId, long resourceKey) {}

  private final Fetcher fetcher;
  private final Map<CacheKey, ScriptResource> cache;

  ScriptResources(int maxEntries) {
    this(maxEntries, ScriptResources::fetchFromCamunda);
  }

  ScriptResources(int maxEntries, Fetcher fetcher) {
    this.fetcher = fetcher;
    this.cache =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<CacheKey, ScriptResource> eldest) {
            return size() > maxEntries;
          }
        };
  }

  ScriptResource get(CamundaClient client, String physicalTenantId, long resourceKey) {
    // Resource keys are only unique within one physical tenant.
    final var key = new CacheKey(physicalTenantId, resourceKey);
    synchronized (cache) {
      final var cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
    }
    final var resource = fetcher.fetch(client, resourceKey);
    synchronized (cache) {
      cache.put(key, resource);
    }
    return resource;
  }

  private static ScriptResource fetchFromCamunda(CamundaClient client, long resourceKey) {
    final var metadata = client.newResourceGetRequest(resourceKey).send().join();
    // The client returns the content as a String. Every worker encodes it back to UTF-8 the same
    // way, so the digest is stable across runtime instances even if the bytes were not UTF-8.
    final var content = client.newResourceContentBinaryGetRequest(resourceKey).send().join();
    return new ScriptResource(metadata.getResourceName(), content.getBytes(StandardCharsets.UTF_8));
  }
}
