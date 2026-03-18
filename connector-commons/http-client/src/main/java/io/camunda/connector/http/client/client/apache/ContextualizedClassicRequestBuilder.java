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

import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * A wrapper around {@link ClassicRequestBuilder} that carries additional context about OAuth2 token
 * handling. This allows part builders (e.g. the authentication builder) to attach metadata such as
 * whether the token was served from cache and a callback to revoke (invalidate) the cached token.
 */
public class ContextualizedClassicRequestBuilder {

  private final ClassicRequestBuilder delegate;
  private boolean wasTokenCached = false;
  private Runnable revokeTokenCallback = () -> {};

  public ContextualizedClassicRequestBuilder(ClassicRequestBuilder delegate) {
    this.delegate = delegate;
  }

  /** Returns the underlying Apache {@link ClassicRequestBuilder}. */
  public ClassicRequestBuilder getDelegate() {
    return delegate;
  }

  /** Whether the OAuth2 token used for this request was served from the cache. */
  public boolean wasTokenCached() {
    return wasTokenCached;
  }

  public void setWasTokenCached(boolean wasTokenCached) {
    this.wasTokenCached = wasTokenCached;
  }

  /**
   * A callback that, when invoked, invalidates the OAuth2 token from the cache. May be {@code null}
   * if no OAuth2 authentication is used.
   */
  public Runnable getRevokeTokenCallback() {
    return revokeTokenCallback;
  }

  public void setRevokeTokenCallback(Runnable revokeTokenCallback) {
    this.revokeTokenCallback = revokeTokenCallback;
  }

  /**
   * Builds the underlying {@link ClassicRequestBuilder} and wraps the result in a {@link
   * ContextualizedClassicHttpRequest} that carries the OAuth2 context forward.
   */
  public ContextualizedClassicHttpRequest build() {
    return new ContextualizedClassicHttpRequest(
        delegate.build(), wasTokenCached, revokeTokenCallback);
  }
}
