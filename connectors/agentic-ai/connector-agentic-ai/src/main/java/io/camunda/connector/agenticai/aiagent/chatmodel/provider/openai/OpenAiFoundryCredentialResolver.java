/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import java.net.URI;
import java.util.Locale;

/**
 * Resolves the openai-java {@link Credential} for the {@code foundry} backend's {@link
 * FoundryAuthentication}: maps API-key auth directly to {@link AzureApiKeyCredential}, and wraps
 * the {@link TokenCredential} resolved by the shared, provider-agnostic {@link
 * EntraIdTokenCredentialFactory} as a {@link BearerTokenCredential} for the two Microsoft Entra ID
 * flows. {@link OpenAiChatModelFactory} only ever calls {@link #credential(String,
 * FoundryAuthentication)}; it never sees a raw {@link TokenCredential}, a cache key, or any secret
 * material one is derived from -- all of that lives in {@link EntraIdTokenCredentialFactory}.
 *
 * <p>The Entra ID token scope depends on which Azure API surface the configured endpoint targets:
 * classic Azure OpenAI ({@code *.openai.azure.com}) and unified Microsoft Foundry ({@code
 * *.services.ai.azure.com}) resources require different token audiences. This is a different axis
 * than the openai-java SDK's own {@code AzureUrlCategory} (legacy vs. unified <em>REST path
 * style</em>, driven by whether the base URL's path ends in {@code /openai/v1}, not by host) --
 * that classification can't be reused here, so the host is inspected directly.
 */
public class OpenAiFoundryCredentialResolver {

  /**
   * Scope for the classic Azure OpenAI API surface ({@code *.openai.azure.com}), per the <a
   * href="https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/managed-identity">Azure
   * OpenAI managed identity documentation</a>.
   */
  private static final String AZURE_OPENAI_SCOPE = "https://cognitiveservices.azure.com/.default";

  /**
   * Scope for the unified Microsoft Foundry Models API surface ({@code *.services.ai.azure.com}),
   * per the <a
   * href="https://learn.microsoft.com/en-us/azure/ai-foundry/foundry-models/how-to/configure-entra-id">
   * Microsoft Foundry Entra ID documentation</a> (the openai-java code sample there uses this exact
   * scope with {@link AuthenticationUtil#getBearerTokenSupplier}).
   */
  private static final String AZURE_AI_FOUNDRY_SCOPE = "https://ai.azure.com/.default";

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory;

  public OpenAiFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    this.entraIdTokenCredentialFactory = entraIdTokenCredentialFactory;
  }

  /**
   * Resolves the openai-java {@link Credential} to apply for the given authentication variant,
   * requesting the Entra ID token scope appropriate for the given Foundry {@code endpoint} where
   * applicable.
   */
  public Credential credential(String endpoint, FoundryAuthentication authentication) {
    return switch (authentication) {
      case FoundryAuthentication.ApiKeyAuthentication auth ->
          AzureApiKeyCredential.create(auth.apiKey());
      case FoundryAuthentication.ClientCredentialsAuthentication auth ->
          entraIdBearerTokenCredential(
              entraIdTokenCredentialFactory.clientCredentials(
                  auth.tenantId(), auth.clientId(), auth.clientSecret(), auth.authorityHost()),
              endpoint);
      case FoundryAuthentication.ManagedIdentityAuthentication auth ->
          entraIdBearerTokenCredential(
              entraIdTokenCredentialFactory.managedIdentity(auth.managedIdentityClientId()),
              endpoint);
    };
  }

  /**
   * Wraps an azure-identity {@link TokenCredential} as an openai-java {@link Credential} via the
   * supplier {@link AuthenticationUtil#getBearerTokenSupplier} builds: it is invoked fresh on every
   * request, so this never caches a token itself, relying entirely on the wrapped credential's own
   * token cache and refresh logic.
   */
  private static Credential entraIdBearerTokenCredential(
      TokenCredential tokenCredential, String endpoint) {
    return BearerTokenCredential.create(
        AuthenticationUtil.getBearerTokenSupplier(tokenCredential, scopeFor(endpoint)));
  }

  /**
   * The unified Microsoft Foundry Models API surface is reached through a {@code
   * *.services.ai.azure.com} resource hostname; every other Foundry-backend endpoint (classic
   * {@code *.openai.azure.com} resources, or anything else the user points the {@code foundry}
   * backend at) is treated as the classic Azure OpenAI surface, its scope's documented default.
   */
  static String scopeFor(String endpoint) {
    final var host = URI.create(endpoint).getHost();
    return host != null && host.toLowerCase(Locale.ROOT).endsWith(".services.ai.azure.com")
        ? AZURE_AI_FOUNDRY_SCOPE
        : AZURE_OPENAI_SCOPE;
  }
}
