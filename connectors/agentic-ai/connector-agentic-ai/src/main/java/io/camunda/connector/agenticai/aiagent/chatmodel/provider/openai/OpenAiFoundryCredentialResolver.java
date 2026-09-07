/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.AZURE_PUBLIC_CLOUD_SCOPE;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.resolveScope;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.scopeFor;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;

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
