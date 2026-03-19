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
package io.camunda.connector.http.client.authentication;

import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;

/**
 * A static holder for the active {@link OAuthTokenCache} instance, acting as a bridge between
 * Spring-managed configuration and non-Spring HTTP client code.
 *
 * <p>In a Spring environment, the cache instance should be set at startup via {@link
 * #set(OAuthTokenCache)} (typically from the auto-configuration). In non-Spring environments,
 * {@link #get()} falls back to a default {@link CaffeineOAuthTokenCache} instance.
 *
 * <p>This allows the HTTP client module (which has no Spring dependency) to use a cache instance
 * that is configured and potentially replaced by the Spring application context.
 */
public final class OAuthTokenCacheHolder {

  private static volatile OAuthTokenCache instance;

  private OAuthTokenCacheHolder() {}

  /**
   * Sets the active {@link OAuthTokenCache} instance. This should be called at application startup
   * before any HTTP requests are made.
   *
   * @param cache the cache instance to use, must not be {@code null}
   * @throws IllegalArgumentException if cache is {@code null}
   */
  public static void set(OAuthTokenCache cache) {
    if (cache == null) {
      throw new IllegalArgumentException("OAuthTokenCache must not be null");
    }
    instance = cache;
  }

  /**
   * Returns the active {@link OAuthTokenCache} instance. If no instance has been configured via
   * {@link #set(OAuthTokenCache)}, a default {@link CaffeineOAuthTokenCache} is created and
   * returned.
   *
   * @return the active cache instance, never {@code null}
   */
  public static OAuthTokenCache get() {
    OAuthTokenCache cache = instance;
    if (cache == null) {
      synchronized (OAuthTokenCacheHolder.class) {
        cache = instance;
        if (cache == null) {
          cache = new CaffeineOAuthTokenCache();
          instance = cache;
        }
      }
    }
    return cache;
  }

  /** Resets the holder to its uninitialized state. Intended for testing only. */
  static void reset() {
    instance = null;
  }
}
