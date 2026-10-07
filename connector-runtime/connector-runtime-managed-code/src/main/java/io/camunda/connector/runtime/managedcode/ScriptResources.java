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
import io.camunda.client.api.command.ClientHttpException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches script and manifest resources by key. Resource content is immutable for a resource key,
 * so it is kept in a bounded, least-recently-used cache.
 *
 * <p>The resource API reads from secondary storage, which the exporter fills after the deployment.
 * A job created right after the deployment can reference a resource that is not visible yet, so a
 * missing resource is polled for up to {@code visibilityTimeout} before it is reported.
 */
final class ScriptResources {

  static final Duration DEFAULT_VISIBILITY_TIMEOUT = Duration.ofSeconds(10);
  static final Duration DEFAULT_VISIBILITY_POLL_INTERVAL = Duration.ofMillis(250);

  @FunctionalInterface
  interface Fetcher {
    /**
     * @throws ResourceNotVisibleException if Camunda does not know the resource (yet)
     */
    ScriptResource fetch(CamundaClient client, long resourceKey);
  }

  /** The resource key was resolved by the engine but the resource API does not return it. */
  static final class ResourceNotVisibleException extends RuntimeException {
    ResourceNotVisibleException(long resourceKey, Throwable cause) {
      super("Resource with key %d not found".formatted(resourceKey), cause);
    }
  }

  /**
   * @param content UTF-8 bytes of the content returned by Camunda; the same array is hashed into
   *     the artifact digest and handed to the provider
   */
  record ScriptResource(String name, byte[] content) {}

  private record CacheKey(String physicalTenantId, long resourceKey) {}

  private final Fetcher fetcher;
  private final Duration visibilityTimeout;
  private final Duration visibilityPollInterval;
  private final Map<CacheKey, ScriptResource> cache;

  ScriptResources(int maxEntries) {
    this(
        maxEntries,
        ScriptResources::fetchFromCamunda,
        DEFAULT_VISIBILITY_TIMEOUT,
        DEFAULT_VISIBILITY_POLL_INTERVAL);
  }

  ScriptResources(
      int maxEntries,
      Fetcher fetcher,
      Duration visibilityTimeout,
      Duration visibilityPollInterval) {
    this.fetcher = fetcher;
    this.visibilityTimeout = visibilityTimeout;
    this.visibilityPollInterval = visibilityPollInterval;
    this.cache =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<CacheKey, ScriptResource> eldest) {
            return size() > maxEntries;
          }
        };
  }

  ScriptResource get(CamundaClient client, String physicalTenantId, long resourceKey)
      throws InterruptedException {
    // Resource keys are only unique within one physical tenant.
    final var key = new CacheKey(physicalTenantId, resourceKey);
    synchronized (cache) {
      final var cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
    }
    final var resource = fetchWhenVisible(client, resourceKey);
    synchronized (cache) {
      cache.put(key, resource);
    }
    return resource;
  }

  private ScriptResource fetchWhenVisible(CamundaClient client, long resourceKey)
      throws InterruptedException {
    final long deadline = System.nanoTime() + visibilityTimeout.toNanos();
    while (true) {
      try {
        return fetcher.fetch(client, resourceKey);
      } catch (ResourceNotVisibleException e) {
        if (System.nanoTime() + visibilityPollInterval.toNanos() > deadline) {
          throw e;
        }
        Thread.sleep(visibilityPollInterval);
      }
    }
  }

  private static ScriptResource fetchFromCamunda(CamundaClient client, long resourceKey) {
    try {
      final var metadata = client.newResourceGetRequest(resourceKey).send().join();
      // The client returns the content as a String. Every worker encodes it back to UTF-8 the same
      // way, so the digest is stable across runtime instances even if the bytes were not UTF-8.
      final var content = client.newResourceContentBinaryGetRequest(resourceKey).send().join();
      return new ScriptResource(
          metadata.getResourceName(), content.getBytes(StandardCharsets.UTF_8));
    } catch (ClientHttpException e) {
      if (e.code() == 404) {
        throw new ResourceNotVisibleException(resourceKey, e);
      }
      throw e;
    }
  }
}
