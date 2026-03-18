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
package io.camunda.connector.http.client.authentication.cacheimpl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.http.client.authentication.CachedTokenResponse;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.TokenResponse;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Caffeine-backed, thread-safe implementation of {@link OAuthTokenCache}.
 *
 * <p>The cache key is derived by hashing the OAuth configuration fields (token endpoint, client ID,
 * client secret, audience, scopes, and client authentication method) using SHA-256 so that
 * sensitive credential material is never stored in plain text as a map key.
 *
 * <p>TTL strategy:
 *
 * <ul>
 *   <li><b>Token-derived TTL</b> – When the token response contains an {@code expires_in} field,
 *       the cache entry expires at {@code expires_in - clockSkewBuffer}. This mode takes precedence
 *       when {@code expires_in} is present.
 *   <li><b>Explicit TTL</b> – A fixed duration used as fallback when {@code expires_in} is not
 *       available. Defaults to 270 seconds (accounting for a typical 300 s token lifetime).
 * </ul>
 */
public class CaffeineOAuthTokenCache implements OAuthTokenCache {

  /** Default explicit TTL when no {@code expires_in} is present in the token response. */
  static final Duration DEFAULT_EXPLICIT_TTL = Duration.ofSeconds(270);

  /** Default clock-skew buffer subtracted from the token-derived TTL. */
  static final Duration DEFAULT_CLOCK_SKEW_BUFFER = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(CaffeineOAuthTokenCache.class);

  private static volatile CaffeineOAuthTokenCache INSTANCE;
  private final Duration explicitTtl;
  private final Duration clockSkewBuffer;
  private final Cache<String, CaffeineCacheToken> cache;

  /** Creates a cache with default TTL settings. */
  public CaffeineOAuthTokenCache() {
    this(DEFAULT_EXPLICIT_TTL, DEFAULT_CLOCK_SKEW_BUFFER);
  }

  /**
   * Creates a cache with custom TTL settings. Intended for testing.
   *
   * @param explicitTtl the fallback TTL when no {@code expires_in} is present
   * @param clockSkewBuffer the buffer subtracted from token-derived TTL
   */
  public CaffeineOAuthTokenCache(Duration explicitTtl, Duration clockSkewBuffer) {
    this.explicitTtl = explicitTtl;
    this.clockSkewBuffer = clockSkewBuffer;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfter(new CaffeineOauthExpiryStrategy())
            .build();
  }

  /**
   * Returns the shared singleton instance of this cache. If no instance has been configured via
   * {@link #initialize(Duration, Duration)}, a default instance is created lazily.
   *
   * <p>This is the recommended way to obtain a {@link CaffeineOAuthTokenCache} in production code
   * to ensure all components share the same token cache.
   *
   * @return the singleton instance
   */
  public static CaffeineOAuthTokenCache getInstance() {
    if (INSTANCE == null) {
      synchronized (CaffeineOAuthTokenCache.class) {
        if (INSTANCE == null) {
          INSTANCE = new CaffeineOAuthTokenCache();
          LOG.debug(
              "Created default CaffeineOAuthTokenCache singleton (ttl={}, skewBuffer={})",
              DEFAULT_EXPLICIT_TTL,
              DEFAULT_CLOCK_SKEW_BUFFER);
        }
      }
    }
    return INSTANCE;
  }

  /**
   * Initializes the singleton instance with the given TTL settings. Must be called before any call
   * to {@link #getInstance()} (e.g. at application startup via Spring configuration). If a
   * singleton already exists, it is replaced. {@code null} values fall back to defaults.
   *
   * @param explicitTtl the fallback TTL when no {@code expires_in} is present, or {@code null} for
   *     default
   * @param clockSkewBuffer the buffer subtracted from token-derived TTL, or {@code null} for
   *     default
   * @return the newly created singleton instance
   */
  public static synchronized CaffeineOAuthTokenCache initialize(
      Duration explicitTtl, Duration clockSkewBuffer) {
    var ttl = explicitTtl != null ? explicitTtl : DEFAULT_EXPLICIT_TTL;
    var skew = clockSkewBuffer != null ? clockSkewBuffer : DEFAULT_CLOCK_SKEW_BUFFER;
    INSTANCE = new CaffeineOAuthTokenCache(ttl, skew);
    LOG.info("Initialized CaffeineOAuthTokenCache singleton (ttl={}, skewBuffer={})", ttl, skew);
    return INSTANCE;
  }

  /**
   * Computes a SHA-256 hash of the OAuth configuration fields to use as the cache key. This ensures
   * that sensitive credential material is never stored in plain text.
   */
  public static String computeCacheKey(OAuthAuthentication auth) {
    String raw =
        String.join(
            "\0",
            nullSafe(auth.oauthTokenEndpoint()),
            nullSafe(auth.clientId()),
            nullSafe(auth.clientSecret()),
            nullSafe(auth.audience()),
            nullSafe(auth.scopes()),
            nullSafe(auth.clientAuthentication()));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is required by the Java spec, so this should never happen
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  @Override
  public CachedTokenResponse getOrFetch(
      OAuthAuthentication auth, Supplier<TokenResponse> tokenSupplier) {
    String cacheKey = computeCacheKey(auth);
    CaffeineCacheToken cached = cache.getIfPresent(cacheKey);
    if (cached != null) {
      LOG.debug("OAuth token cache hit");
      return new CachedTokenResponse(cached.accessToken(), true);
    }
    LOG.debug("OAuth token cache miss, fetching new token");
    TokenResponse response = tokenSupplier.get();
    long ttlNanos = computeTtlNanos(response);
    if (ttlNanos > 0) {
      cache.put(cacheKey, new CaffeineCacheToken(response.accessToken(), ttlNanos));
    }
    return new CachedTokenResponse(response.accessToken(), false);
  }

  @Override
  public void invalidate(OAuthAuthentication auth) {
    String cacheKey = computeCacheKey(auth);
    cache.invalidate(cacheKey);
    LOG.debug("OAuth token cache entry invalidated");
  }

  @Override
  public void invalidateAll() {
    cache.invalidateAll();
    LOG.debug("OAuth token cache fully cleared");
  }

  private long computeTtlNanos(TokenResponse response) {
    Duration ttl;
    if (response.expiresInSeconds().isPresent()) {
      long expiresIn = response.expiresInSeconds().getAsLong();
      Duration tokenDerived = Duration.ofSeconds(expiresIn).minus(clockSkewBuffer);
      ttl = tokenDerived.isPositive() ? tokenDerived : Duration.ZERO;
      LOG.debug(
          "Using token-derived TTL: {} seconds (expires_in={}, clockSkewBuffer={})",
          ttl.toSeconds(),
          expiresIn,
          clockSkewBuffer.toSeconds());
    } else {
      ttl = explicitTtl;
      LOG.debug("No expires_in in token response, using explicit TTL: {} seconds", ttl.toSeconds());
    }
    return ttl.toNanos();
  }
}
