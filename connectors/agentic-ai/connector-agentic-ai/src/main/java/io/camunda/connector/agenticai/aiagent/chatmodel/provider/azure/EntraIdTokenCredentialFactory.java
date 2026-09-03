/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.util.HttpClientOptions;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.cache.HashedCacheKey;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Resolves and caches azure-identity {@link TokenCredential} instances for Microsoft Entra ID
 * authentication (client-credentials and managed-identity flows). Returns a plain {@link
 * TokenCredential}; wrapping it for a particular vendor SDK is the caller's responsibility.
 *
 * <p>A provider's {@code ChatModel} is typically rebuilt on every agent turn, so without caching, a
 * fresh {@code ClientSecretCredential}/{@code ManagedIdentityCredential} would be constructed every
 * turn, discarding azure-identity's own credential-instance-level token cache and forcing a full
 * Entra ID token request per turn (and risking IMDS throttling for managed identity). Only the
 * credential <em>object</em> is cached here, never a token: azure-identity's credentials already
 * cache and auto-refresh their own tokens internally.
 *
 * <p>The cache key is derived via {@link HashedCacheKey} so that raw credential material such as a
 * client secret is never stored in plain text as a map key.
 *
 * <p>The client-credentials flow also routes its token-exchange request to Microsoft Entra ID
 * through the configured HTTP proxy ({@link AgenticAiHttpProxySupport}), so a Foundry deployment
 * that requires an egress proxy for its OpenAI API calls doesn't unexpectedly bypass it for the
 * token exchange too. Managed identity does not: see {@link
 * #buildManagedIdentityCredential(String)}.
 */
public class EntraIdTokenCredentialFactory {

  private final Cache<String, TokenCredential> cache;
  private final AgenticAiHttpProxySupport httpProxySupport;

  public EntraIdTokenCredentialFactory(
      AgenticAiHttpProxySupport httpProxySupport, CredentialCacheProperties properties) {
    this.httpProxySupport = httpProxySupport;
    final long maximumSize = properties.enabled() ? properties.maximumSize() : 0;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(properties.expireAfterAccess())
            .build();
  }

  /**
   * Returns the cached (or newly built and cached) {@link TokenCredential} for a Microsoft Entra ID
   * client-credentials (app registration + secret) flow.
   */
  public TokenCredential clientCredentials(
      String tenantId, String clientId, String clientSecret, @Nullable String authorityHost) {
    final var key = HashedCacheKey.of(tenantId, clientId, clientSecret, authorityHost);
    return cache.get(
        key,
        k ->
            buildClientSecretCredential(
                httpProxySupport, tenantId, clientId, clientSecret, authorityHost));
  }

  /**
   * Returns the cached (or newly built and cached) {@link TokenCredential} for a Microsoft Entra ID
   * managed-identity flow. {@code clientId} selects a user-assigned identity; {@code null} resolves
   * the system-assigned identity.
   */
  public TokenCredential managedIdentity(@Nullable String clientId) {
    final var key = HashedCacheKey.of(clientId);
    return cache.get(key, k -> buildManagedIdentityCredential(clientId));
  }

  private static TokenCredential buildClientSecretCredential(
      AgenticAiHttpProxySupport httpProxySupport,
      String tenantId,
      String clientId,
      String clientSecret,
      @Nullable String authorityHost) {
    final var clientSecretCredentialBuilder =
        new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId);
    if (authorityHost != null && !authorityHost.isBlank()) {
      clientSecretCredentialBuilder.authorityHost(authorityHost);
    }
    httpProxySupport
        .azureProxyOptions(ProxyConfiguration.SCHEME_HTTPS)
        .ifPresent(
            proxyOptions ->
                clientSecretCredentialBuilder.httpClient(
                    HttpClient.createDefault(
                        new HttpClientOptions().setProxyOptions(proxyOptions))));
    return clientSecretCredentialBuilder.build();
  }

  /**
   * The managed-identity token request never goes through the configured proxy: it targets the
   * link-local IMDS endpoint ({@code 169.254.169.254}) or an environment-provided local sidecar
   * endpoint, neither of which is reachable via an internet-facing egress proxy -- Microsoft's own
   * IMDS guidance explicitly calls out bypassing any configured proxy for this address.
   */
  private static TokenCredential buildManagedIdentityCredential(@Nullable String clientId) {
    final var managedIdentityCredentialBuilder = new ManagedIdentityCredentialBuilder();
    if (clientId != null && !clientId.isBlank()) {
      managedIdentityCredentialBuilder.clientId(clientId);
    }
    return managedIdentityCredentialBuilder.build();
  }
}
