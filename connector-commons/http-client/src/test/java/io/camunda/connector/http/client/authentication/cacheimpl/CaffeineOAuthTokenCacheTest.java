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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.TokenResponse;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CaffeineOAuthTokenCacheTest {

  private static OAuthAuthentication createAuth(
      String endpoint, String clientId, String clientSecret) {
    return new OAuthAuthentication(
        endpoint, clientId, clientSecret, "audience", OAuthConstants.CREDENTIALS_BODY, "scope");
  }

  @Nested
  class CacheKeyTests {

    @Test
    void shouldProduceSameKey_forSameAuthentication() {
      var auth1 = createAuth("https://token.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token.example.com", "id1", "secret1");

      String key1 = CaffeineOAuthTokenCache.computeCacheKey(auth1);
      String key2 = CaffeineOAuthTokenCache.computeCacheKey(auth2);

      assertThat(key1).isEqualTo(key2);
    }

    @Test
    void shouldProduceDifferentKey_forDifferentClientId() {
      var auth1 = createAuth("https://token.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token.example.com", "id2", "secret1");

      String key1 = CaffeineOAuthTokenCache.computeCacheKey(auth1);
      String key2 = CaffeineOAuthTokenCache.computeCacheKey(auth2);

      assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldProduceDifferentKey_forDifferentEndpoint() {
      var auth1 = createAuth("https://token1.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token2.example.com", "id1", "secret1");

      String key1 = CaffeineOAuthTokenCache.computeCacheKey(auth1);
      String key2 = CaffeineOAuthTokenCache.computeCacheKey(auth2);

      assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldProduceDifferentKey_forDifferentSecret() {
      var auth1 = createAuth("https://token.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token.example.com", "id1", "secret2");

      String key1 = CaffeineOAuthTokenCache.computeCacheKey(auth1);
      String key2 = CaffeineOAuthTokenCache.computeCacheKey(auth2);

      assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldProduceHexString_ofSha256Length() {
      var auth = createAuth("https://token.example.com", "id1", "secret1");
      String key = CaffeineOAuthTokenCache.computeCacheKey(auth);

      // SHA-256 produces 32 bytes = 64 hex characters
      assertThat(key).hasSize(64);
      assertThat(key).matches("[0-9a-f]+");
    }
  }

  @Nested
  class GetOrFetchTests {

    @Test
    void shouldReturnCachedToken_onSecondCall() {
      var cache = new CaffeineOAuthTokenCache();
      var auth = createAuth("https://token.example.com", "id1", "secret1");
      var fetchCount = new AtomicInteger(0);

      // First call - fetches token
      var result1 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-abc", 300);
              });

      // Second call - should return cached token without calling supplier
      var result2 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-xyz", 300);
              });

      assertThat(result1).isEqualTo("token-abc");
      assertThat(result2).isEqualTo("token-abc");
      assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    void shouldFetchNewToken_forDifferentAuth() {
      var cache = new CaffeineOAuthTokenCache();
      var auth1 = createAuth("https://token.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token.example.com", "id2", "secret2");

      var result1 = cache.getOrFetch(auth1, () -> new TokenResponse("token-for-id1", 300));
      var result2 = cache.getOrFetch(auth2, () -> new TokenResponse("token-for-id2", 300));

      assertThat(result1).isEqualTo("token-for-id1");
      assertThat(result2).isEqualTo("token-for-id2");
    }

    @Test
    void shouldReturnToken_whenExpiresInIsAbsent_andNotCacheIt() {
      var cache = new CaffeineOAuthTokenCache();
      var auth = createAuth("https://token.example.com", "id1", "secret1");
      var fetchCount = new AtomicInteger(0);

      var result1 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-no-expiry-1");
              });

      var result2 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-no-expiry-2");
              });

      assertThat(result1).isEqualTo("token-no-expiry-1");
      assertThat(result2).isEqualTo("token-no-expiry-2");
      assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    void shouldNotCache_whenTtlIsZeroOrNegative() {
      // clockSkewBuffer > expiresIn => effective TTL is 0, so token should not be cached
      var cache = new CaffeineOAuthTokenCache(Duration.ofSeconds(100));
      var auth = createAuth("https://token.example.com", "id1", "secret1");
      var fetchCount = new AtomicInteger(0);

      var result1 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-a", 50); // 50s - 100s skew = negative
              });

      var result2 =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-b", 50);
              });

      assertThat(result1).isEqualTo("token-a");
      assertThat(result2).isEqualTo("token-b");
      assertThat(fetchCount.get()).isEqualTo(2);
    }
  }

  @Nested
  class InvalidateTests {

    @Test
    void shouldFetchNewToken_afterInvalidation() {
      var cache = new CaffeineOAuthTokenCache();
      var auth = createAuth("https://token.example.com", "id1", "secret1");
      var fetchCount = new AtomicInteger(0);

      cache.getOrFetch(
          auth,
          () -> {
            fetchCount.incrementAndGet();
            return new TokenResponse("token-1", 300);
          });

      cache.invalidate(auth);

      var result =
          cache.getOrFetch(
              auth,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("token-2", 300);
              });

      assertThat(result).isEqualTo("token-2");
      assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    void shouldNotAffectOtherEntries_whenInvalidatingOne() {
      var cache = new CaffeineOAuthTokenCache();
      var auth1 = createAuth("https://token.example.com", "id1", "secret1");
      var auth2 = createAuth("https://token.example.com", "id2", "secret2");

      cache.getOrFetch(auth1, () -> new TokenResponse("token-1", 300));
      cache.getOrFetch(auth2, () -> new TokenResponse("token-2", 300));

      cache.invalidate(auth1);

      var fetchCount = new AtomicInteger(0);
      var result2 =
          cache.getOrFetch(
              auth2,
              () -> {
                fetchCount.incrementAndGet();
                return new TokenResponse("should-not-fetch", 300);
              });

      assertThat(result2).isEqualTo("token-2");
      assertThat(fetchCount.get()).isEqualTo(0);
    }
  }

  @Nested
  class StatsTests {

    @Test
    void shouldReportHitAndMissStats_forCachedFlow() {
      var cache = new CaffeineOAuthTokenCache();
      var auth = createAuth("https://token.example.com", "id1", "secret1");

      cache.getOrFetch(auth, () -> new TokenResponse("token-1", 300));
      cache.getOrFetch(auth, () -> new TokenResponse("token-2", 300));

      var stats = cache.getStats();
      assertThat(stats.missCount()).isEqualTo(1);
      assertThat(stats.hitCount()).isEqualTo(1);
      assertThat(stats.estimatedSize()).isEqualTo(1);
      assertThat(stats.hitRate()).isGreaterThan(0.0);
    }

    @Test
    void shouldReportMissesOnly_whenTokensAreNotCached() {
      var cache = new CaffeineOAuthTokenCache();
      var auth = createAuth("https://token.example.com", "id1", "secret1");

      cache.getOrFetch(auth, () -> new TokenResponse("token-no-expiry-1"));
      cache.getOrFetch(auth, () -> new TokenResponse("token-no-expiry-2"));

      var stats = cache.getStats();
      assertThat(stats.missCount()).isEqualTo(2);
      assertThat(stats.hitCount()).isEqualTo(0);
      assertThat(stats.estimatedSize()).isEqualTo(0);
      assertThat(stats.hitRate()).isEqualTo(0.0);
    }
  }
}
