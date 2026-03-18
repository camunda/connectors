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
package io.camunda.connector.http.client.client.apache;

import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * A wrapper around {@link ClassicHttpRequest} that carries additional context about OAuth2 token
 * handling. This enables downstream consumers (e.g. retry logic) to know whether the token was
 * cached and to revoke it from the cache if needed (e.g. on a 401 response).
 */
public class ContextualizedClassicHttpRequest {

  private final ClassicHttpRequest request;
  private final boolean wasTokenCached;
  private final Runnable revokeTokenCallback;

  public ContextualizedClassicHttpRequest(
      ClassicHttpRequest request, boolean wasTokenCached, Runnable revokeTokenCallback) {
    this.request = request;
    this.wasTokenCached = wasTokenCached;
    this.revokeTokenCallback = revokeTokenCallback;
  }

  /** Returns the underlying Apache {@link ClassicHttpRequest}. */
  public ClassicHttpRequest getRequest() {
    return request;
  }

  /** Whether the OAuth2 token used for this request was served from the cache. */
  public boolean wasTokenCached() {
    return wasTokenCached;
  }

  /**
   * A callback that, when invoked, invalidates the OAuth2 token from the cache. May be {@code null}
   * if no OAuth2 authentication is used.
   */
  public Runnable getRevokeTokenCallback() {
    return revokeTokenCallback;
  }
}
