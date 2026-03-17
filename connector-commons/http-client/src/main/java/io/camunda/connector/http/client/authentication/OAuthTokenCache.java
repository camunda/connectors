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

import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.util.function.Supplier;

/**
 * A thread-safe cache for OAuth2 access tokens, keyed by the combination of token endpoint URL and
 * credentials. Implementations must ensure that sensitive credential material is never stored or
 * logged in plain text.
 */
public interface OAuthTokenCache {

  /**
   * Returns a cached token for the given authentication configuration, or fetches a new one using
   * the provided supplier and caches it according to the configured TTL strategy.
   *
   * @param auth the OAuth authentication configuration used to derive the cache key
   * @param tokenSupplier a supplier that fetches a new token from the token endpoint
   * @return the access token string
   */
  String getOrFetch(OAuthAuthentication auth, Supplier<TokenResponse> tokenSupplier);

  /**
   * Invalidates the cached token for the given authentication configuration, e.g. after receiving a
   * 401 response from a downstream service.
   *
   * @param auth the OAuth authentication configuration whose cached token should be removed
   */
  void invalidate(OAuthAuthentication auth);

  /** Invalidates all cached tokens. */
  void invalidateAll();
}
