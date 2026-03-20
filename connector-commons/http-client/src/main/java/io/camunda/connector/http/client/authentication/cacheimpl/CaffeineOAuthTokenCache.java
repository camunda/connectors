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
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.TokenResponse;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
 *       the cache entry expires at {@code expires_in - clockSkewBuffer}.
 *   <li><b>No caching</b> – When the token response does not contain {@code expires_in}, the token
 *       is returned directly without being cached.
 * </ul>
 */
public class CaffeineOAuthTokenCache implements OAuthTokenCache {

  /** Default clock-skew buffer subtracted from the token-derived TTL. */
  static final Duration DEFAULT_CLOCK_SKEW_BUFFER = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(CaffeineOAuthTokenCache.class);
  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(CaffeineOAuthTokenCache::createSha256Digest);

  private final Duration clockSkewBuffer;
  private final Cache<String, CaffeineCacheToken> cache;

  /** Creates a cache with default TTL settings. */
  public CaffeineOAuthTokenCache() {
    this(DEFAULT_CLOCK_SKEW_BUFFER);
  }

  /**
   * Creates a cache with custom TTL settings. Intended for testing.
   *
   * @param clockSkewBuffer the buffer subtracted from token-derived TTL
   */
  public CaffeineOAuthTokenCache(Duration clockSkewBuffer) {
    this.clockSkewBuffer = clockSkewBuffer;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfter(new CaffeineOauthExpiryStrategy())
            .build();
  }

  /**
   * Creates a new instance with the given TTL settings. {@code null} values fall back to defaults.
   *
   * @param clockSkewBuffer the buffer subtracted from token-derived TTL, or {@code null} for
   *     default
   * @return a new cache instance
   */
  public static CaffeineOAuthTokenCache initialize(Duration clockSkewBuffer) {
    var skew = clockSkewBuffer != null ? clockSkewBuffer : DEFAULT_CLOCK_SKEW_BUFFER;
    LOG.info("Creating CaffeineOAuthTokenCache (skewBuffer={})", skew);
    return new CaffeineOAuthTokenCache(skew);
  }

  /**
   * Computes a SHA-256 hash of the OAuth configuration fields to use as the cache key. This ensures
   * that sensitive credential material is never stored in plain text.
   */
  static String computeCacheKey(OAuthAuthentication auth) {
    String raw =
        String.join(
            "\0",
            Objects.requireNonNullElse(auth.oauthTokenEndpoint(), ""),
            Objects.requireNonNullElse(auth.clientId(), ""),
            Objects.requireNonNullElse(auth.clientSecret(), ""),
            Objects.requireNonNullElse(auth.audience(), ""),
            Objects.requireNonNullElse(auth.scopes(), ""),
            Objects.requireNonNullElse(auth.clientAuthentication(), ""));

    MessageDigest digest = SHA_256_DIGEST.get();
    digest.reset();
    byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }

  private static MessageDigest createSha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is required by the Java spec, so this should never happen
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @Override
  public String getOrFetch(OAuthAuthentication auth, Supplier<TokenResponse> tokenSupplier) {
    String cacheKey = computeCacheKey(auth);

    AtomicReference<String> uncachedToken = new AtomicReference<>();

    CaffeineCacheToken entry =
        cache.get(
            cacheKey,
            k -> {
              LOG.debug("OAuth token cache miss, fetching new token");
              TokenResponse response = tokenSupplier.get();
              if (response.expiresInSeconds().isEmpty()) {
                LOG.debug("No expires_in in token response, token will not be cached");
                uncachedToken.set(response.accessToken());
                return null; // Caffeine will not cache a null mapping
              } else {
                return new CaffeineCacheToken(
                    response.accessToken(),
                    computeTtlNanos(response.expiresInSeconds().getAsLong()));
              }
            });

    if (entry != null) {
      LOG.debug("OAuth token cache hit");
      return entry.accessToken();
    }

    // Mapping returned null → token was fetched but not cached (no expires_in)
    return uncachedToken.get();
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

  private long computeTtlNanos(long expiresIn) {
    Duration tokenDerived = Duration.ofSeconds(expiresIn).minus(clockSkewBuffer);
    Duration ttl = tokenDerived.isPositive() ? tokenDerived : Duration.ZERO;
    LOG.debug(
        "Using token-derived TTL: {} seconds (expires_in={}, clockSkewBuffer={})",
        ttl.toSeconds(),
        expiresIn,
        clockSkewBuffer.toSeconds());
    return ttl.toNanos();
  }
}
