/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.OpenAiProperties.FoundryProperties.CredentialCacheProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Resolves the openai-java {@link Credential} for the {@code foundry} backend's {@link
 * FoundryAuthentication}, encapsulating everything Azure/Microsoft Entra ID specific: which SDK
 * credential type each authentication variant maps to, the Entra ID token scope, and -- for the two
 * Entra ID flows -- reuse of the underlying azure-identity {@link TokenCredential} across the
 * {@code foundry} backend's per-turn {@code ChatModel} rebuild cycle. {@link
 * OpenAiChatModelFactory} only ever calls {@link #credential(FoundryAuthentication)}; it never sees
 * a raw {@link TokenCredential}, a cache key, or any secret material this class derives one from.
 *
 * <p>A {@code ChatModel} (and the underlying {@code OpenAIClient}) is rebuilt on every agent turn,
 * so without caching, a fresh {@code ClientSecretCredential}/{@code ManagedIdentityCredential}
 * would be constructed on every turn, discarding azure-identity's own credential-instance-level
 * token cache and forcing a full Entra ID token request per turn (and risking IMDS throttling for
 * managed identity). Only the credential <em>object</em> is cached here, never a token:
 * azure-identity's credentials already cache and auto-refresh their own tokens internally.
 *
 * <p>The cache key is a SHA-256 hash of the authentication configuration (mirroring {@code
 * CaffeineOAuthTokenCache} in connector-commons/http-client), computed and consumed entirely inside
 * this class so that raw credential material such as a client secret is never stored in plain text
 * as a map key, or passed to/through any other component.
 */
public class FoundryCredentialResolver {

  /** Scope requested when acquiring a Microsoft Entra ID token for Azure OpenAI / Foundry. */
  private static final String AZURE_COGNITIVE_SERVICES_SCOPE =
      "https://cognitiveservices.azure.com/.default";

  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(FoundryCredentialResolver::createSha256Digest);

  private final Cache<String, TokenCredential> cache;

  public FoundryCredentialResolver(CredentialCacheProperties properties) {
    final long maximumSize = properties.enabled() ? properties.maximumSize() : 0;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(properties.expireAfterAccess())
            .build();
  }

  /** Resolves the openai-java {@link Credential} to apply for the given authentication variant. */
  public Credential credential(FoundryAuthentication authentication) {
    return switch (authentication) {
      case FoundryAuthentication.ApiKeyAuthentication auth ->
          AzureApiKeyCredential.create(auth.apiKey());
      case FoundryAuthentication.ClientCredentialsAuthentication auth ->
          entraIdBearerTokenCredential(tokenCredential(auth));
      case FoundryAuthentication.ManagedIdentityAuthentication auth ->
          entraIdBearerTokenCredential(tokenCredential(auth));
    };
  }

  /** Package-private test seam: exposes the cached credential object itself, nothing else. */
  TokenCredential tokenCredential(FoundryAuthentication.ClientCredentialsAuthentication auth) {
    return cache.get(sha256Hex(clientCredentialsCacheKey(auth)), key -> buildTokenCredential(auth));
  }

  /** Package-private test seam: exposes the cached credential object itself, nothing else. */
  TokenCredential tokenCredential(FoundryAuthentication.ManagedIdentityAuthentication auth) {
    return cache.get(sha256Hex(managedIdentityCacheKey(auth)), key -> buildTokenCredential(auth));
  }

  /**
   * Wraps an azure-identity {@link TokenCredential} as an openai-java {@link Credential}: the
   * supplier is invoked fresh on every request, so this never caches a token itself, relying
   * entirely on the wrapped credential's own token cache and refresh logic.
   */
  private static Credential entraIdBearerTokenCredential(TokenCredential tokenCredential) {
    return BearerTokenCredential.create(
        () ->
            Objects.requireNonNull(
                    tokenCredential
                        .getToken(
                            new TokenRequestContext().addScopes(AZURE_COGNITIVE_SERVICES_SCOPE))
                        .block())
                .getToken());
  }

  private static TokenCredential buildTokenCredential(
      FoundryAuthentication.ClientCredentialsAuthentication auth) {
    final var clientSecretCredentialBuilder =
        new ClientSecretCredentialBuilder()
            .clientId(auth.clientId())
            .clientSecret(auth.clientSecret())
            .tenantId(auth.tenantId());
    if (auth.authorityHost() != null && !auth.authorityHost().isBlank()) {
      clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
    }
    return clientSecretCredentialBuilder.build();
  }

  private static TokenCredential buildTokenCredential(
      FoundryAuthentication.ManagedIdentityAuthentication auth) {
    final var managedIdentityCredentialBuilder = new ManagedIdentityCredentialBuilder();
    if (auth.managedIdentityClientId() != null && !auth.managedIdentityClientId().isBlank()) {
      managedIdentityCredentialBuilder.clientId(auth.managedIdentityClientId());
    }
    return managedIdentityCredentialBuilder.build();
  }

  private static String clientCredentialsCacheKey(
      FoundryAuthentication.ClientCredentialsAuthentication auth) {
    return String.join(
        "\0",
        "clientCredentials",
        auth.tenantId(),
        auth.clientId(),
        auth.clientSecret(),
        Objects.requireNonNullElse(auth.authorityHost(), ""));
  }

  private static String managedIdentityCacheKey(
      FoundryAuthentication.ManagedIdentityAuthentication auth) {
    return String.join(
        "\0", "managedIdentity", Objects.requireNonNullElse(auth.managedIdentityClientId(), ""));
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
