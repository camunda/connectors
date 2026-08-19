/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.azure.core.credential.TokenCredential;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.OpenAiProperties.FoundryProperties.CredentialCacheProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Caches azure-identity {@link TokenCredential} instances (e.g. {@code ClientSecretCredential},
 * {@code ManagedIdentityCredential}) across the {@code Foundry} backend's per-turn {@link
 * OpenAiChatModelFactory#create} cycle. A {@code ChatModel} is rebuilt and closed on every agent
 * turn, so without this cache a fresh credential would be constructed for every LLM call, throwing
 * away azure-identity's own credential-instance-level token cache and forcing a full Microsoft
 * Entra ID token request per turn (and risking IMDS throttling for managed identity).
 *
 * <p>Only the credential <em>object</em> is cached here, never a token: azure-identity's
 * credentials already cache and auto-refresh their own tokens internally, so this cache's only job
 * is reusing that object across factory invocations. The cache key is a SHA-256 hash of the
 * authentication configuration (mirroring {@code CaffeineOAuthTokenCache} in
 * connector-commons/http-client) so that raw credential material such as a client secret is never
 * stored in plain text as a map key.
 */
public class FoundryCredentialCache {

  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(FoundryCredentialCache::createSha256Digest);

  private final Cache<String, TokenCredential> cache;

  public FoundryCredentialCache(CredentialCacheProperties properties) {
    final long maximumSize = properties.enabled() ? properties.maximumSize() : 0;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(properties.expireAfterAccess())
            .build();
  }

  /**
   * Returns the cached {@link TokenCredential} for the given key material, creating and caching a
   * new one via {@code factory} on a cache miss.
   *
   * @param keyMaterial fields identifying this credential configuration (including secrets), joined
   *     by callers using a delimiter that cannot appear in any single field; hashed before use as
   *     the actual cache key, never stored or logged verbatim
   */
  public TokenCredential getOrCreate(String keyMaterial, Supplier<TokenCredential> factory) {
    return cache.get(sha256Hex(keyMaterial), key -> factory.get());
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
