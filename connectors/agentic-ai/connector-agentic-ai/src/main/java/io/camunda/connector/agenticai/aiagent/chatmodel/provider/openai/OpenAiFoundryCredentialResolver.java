/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import com.azure.identity.AzureAuthorityHosts;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the openai-java {@link Credential} for the {@code foundry} backend's {@link
 * FoundryAuthentication}: maps API-key auth directly to {@link AzureApiKeyCredential}, and wraps
 * the {@link TokenCredential} resolved by the shared, provider-agnostic {@link
 * EntraIdTokenCredentialFactory} as a {@link BearerTokenCredential} for the two Microsoft Entra ID
 * flows. {@link OpenAiChatModelFactory} only ever calls {@link #credential(FoundryAuthentication)};
 * it never sees a raw {@link TokenCredential}, a cache key, or any secret material one is derived
 * from -- all of that lives in {@link EntraIdTokenCredentialFactory}.
 */
public class OpenAiFoundryCredentialResolver {

  /**
   * Scope requested for tenants in the Azure Public Cloud, per the <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints">Microsoft
   * Foundry endpoints documentation</a>: the unified OpenAI/v1 API surface -- which {@link
   * OpenAiChatModelFactory} always targets, for both classic Azure OpenAI ({@code
   * *.openai.azure.com}) and Foundry ({@code *.services.ai.azure.com}) resources alike -- uses this
   * one scope regardless of host, within a given cloud.
   */
  private static final String AZURE_PUBLIC_CLOUD_SCOPE = "https://ai.azure.com/.default";

  /**
   * Scope for tenants in the Azure US Government Cloud, per <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/concepts/foundry-azure-government">Microsoft
   * Foundry in Azure Government</a> (portal/endpoints under {@code *.azure.us}) -- the only other
   * sovereign cloud Foundry supports today.
   */
  private static final String AZURE_GOVERNMENT_SCOPE = "https://ai.azure.us/.default";

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory;

  public OpenAiFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    this.entraIdTokenCredentialFactory = entraIdTokenCredentialFactory;
  }

  /**
   * Resolves the openai-java {@link Credential} to apply for the given authentication variant. For
   * either Entra ID flow, that variant's own {@code entraIdScope} escape hatch, when set, wins over
   * the scope this class would otherwise derive.
   */
  public Credential credential(FoundryAuthentication authentication) {
    return switch (authentication) {
      case FoundryAuthentication.ApiKeyAuthentication auth ->
          AzureApiKeyCredential.create(auth.apiKey());
      case FoundryAuthentication.ClientCredentialsAuthentication auth ->
          entraIdBearerTokenCredential(
              entraIdTokenCredentialFactory.clientCredentials(
                  auth.tenantId(), auth.clientId(), auth.clientSecret(), auth.authorityHost()),
              resolveScope(scopeFor(auth.authorityHost()), auth.entraIdScope()));
      // No authorityHost field to key off for managed identity -- Azure Public Cloud only for now,
      // unless entraIdScope steps in.
      case FoundryAuthentication.ManagedIdentityAuthentication auth ->
          entraIdBearerTokenCredential(
              entraIdTokenCredentialFactory.managedIdentity(auth.clientId()),
              resolveScope(AZURE_PUBLIC_CLOUD_SCOPE, auth.entraIdScope()));
    };
  }

  /**
   * Maps an (optional) Microsoft Entra ID {@code authorityHost} override to the matching Foundry
   * scope: an unset/blank host, or one that doesn't match a known sovereign cloud, is Azure Public
   * Cloud; {@link AzureAuthorityHosts#AZURE_GOVERNMENT} is the one other cloud Foundry ships in.
   */
  private static String scopeFor(@Nullable String authorityHost) {
    if (authorityHost == null || authorityHost.isBlank()) {
      return AZURE_PUBLIC_CLOUD_SCOPE;
    }

    final var isGovernmentCloud =
        normalizeAuthorityHost(authorityHost)
            .equals(normalizeAuthorityHost(AzureAuthorityHosts.AZURE_GOVERNMENT));
    return isGovernmentCloud ? AZURE_GOVERNMENT_SCOPE : AZURE_PUBLIC_CLOUD_SCOPE;
  }

  private static String resolveScope(String derivedScope, @Nullable String scopeOverride) {
    return scopeOverride != null && !scopeOverride.isBlank() ? scopeOverride : derivedScope;
  }

  private static String normalizeAuthorityHost(String authorityHost) {
    final var lowerCased = authorityHost.strip().toLowerCase(Locale.ROOT);
    return lowerCased.endsWith("/") ? lowerCased : lowerCased + "/";
  }

  /**
   * Wraps an azure-identity {@link TokenCredential} as an openai-java {@link Credential} via the
   * supplier {@link AuthenticationUtil#getBearerTokenSupplier} builds: it is invoked fresh on every
   * request, so this never caches a token itself, relying entirely on the wrapped credential's own
   * token cache and refresh logic.
   */
  private static Credential entraIdBearerTokenCredential(
      TokenCredential tokenCredential, String scope) {
    return BearerTokenCredential.create(
        AuthenticationUtil.getBearerTokenSupplier(tokenCredential, scope));
  }
}
