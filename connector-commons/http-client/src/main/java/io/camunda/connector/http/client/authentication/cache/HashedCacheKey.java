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
package io.camunda.connector.http.client.authentication.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Derives a SHA-256-hashed cache key from credential-configuration fields, so that sensitive
 * material (client secrets, tokens) is never stored in plain text as a map key. Shared between
 * {@code CaffeineOAuthTokenCache} (connector-commons/http-client) and the Agentic AI module's
 * {@code EntraIdTokenCredentialFactory}, which both hash a tuple of credential fields to key an
 * in-memory Caffeine cache. Only key derivation is shared: the two caches' value types, sizing and
 * expiry policies differ intentionally and are not unified here.
 */
public final class HashedCacheKey {

  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(HashedCacheKey::createSha256Digest);

  private HashedCacheKey() {}

  /** Hashes the given parts, joined with a NUL separator; {@code null} parts count as empty. */
  public static String of(String... parts) {
    final var raw =
        String.join(
            "\0", Arrays.stream(parts).map(part -> Objects.requireNonNullElse(part, "")).toList());
    return sha256Hex(raw);
  }

  private static String sha256Hex(String raw) {
    final MessageDigest digest = SHA_256_DIGEST.get();
    digest.reset();
    final byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
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
}
