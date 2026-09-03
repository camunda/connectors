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
import java.util.HexFormat;
import java.util.Objects;

/**
 * Derives a SHA-256-hashed cache key from a tuple of credential-configuration fields, so that
 * sensitive material (client secrets, tokens) is never stored in plain text as a cache key.
 */
public final class HashedCacheKey {

  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(HashedCacheKey::createSha256Digest);

  private HashedCacheKey() {}

  /**
   * Hashes the given parts; {@code null} parts count as empty. Each part is length-prefixed before
   * hashing, so parts can never be split differently to collide on the same digest.
   */
  public static String of(String... parts) {
    final MessageDigest digest = SHA_256_DIGEST.get();
    digest.reset();
    for (final String part : parts) {
      final byte[] bytes = Objects.requireNonNullElse(part, "").getBytes(StandardCharsets.UTF_8);
      digest.update(
          new byte[] {
            (byte) (bytes.length >>> 24),
            (byte) (bytes.length >>> 16),
            (byte) (bytes.length >>> 8),
            (byte) bytes.length
          });
      digest.update(bytes);
    }
    return HexFormat.of().formatHex(digest.digest());
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
