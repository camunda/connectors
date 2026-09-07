/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.AZURE_PUBLIC_CLOUD_SCOPE;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.resolveScope;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdFoundryScopeResolver.scopeFor;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicFoundryAuthentication.ClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicFoundryAuthentication.ManagedIdentityAuthentication;
import java.util.function.Supplier;

/**
 * Resolves the bearer-token {@link Supplier} the Anthropic SDK's {@code FoundryBackend} wants for
 * Microsoft Entra ID authentication (client-credentials and managed-identity flows), by wrapping
 * the {@link TokenCredential} resolved by the shared, provider-agnostic {@link
 * EntraIdTokenCredentialFactory}. {@code FoundryBackend} calls the supplier fresh on every request,
 * so no token is cached here -- only the underlying {@code TokenCredential} object is (inside
 * {@link EntraIdTokenCredentialFactory}), which already caches and auto-refreshes its own tokens.
 *
 * <p>Structurally mirrors {@code OpenAiFoundryCredentialResolver}, but returns a plain {@code
 * Supplier<String>} rather than an SDK {@code Credential} object: the Anthropic Foundry backend's
 * bearer-token hook is a bare supplier, unlike openai-java's {@code Credential} abstraction.
 */
public class AnthropicFoundryCredentialResolver {

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory;

  public AnthropicFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    this.entraIdTokenCredentialFactory = entraIdTokenCredentialFactory;
  }

  /**
   * Resolves the bearer-token supplier for a client-credentials (app registration + secret) flow.
   * The authentication's own {@code entraIdScope} escape hatch, when set, wins over the scope this
   * class would otherwise derive from {@code authorityHost}.
   */
  public Supplier<String> bearerTokenSupplier(ClientCredentialsAuthentication authentication) {
    final var tokenCredential =
        entraIdTokenCredentialFactory.clientCredentials(
            authentication.tenantId(),
            authentication.clientId(),
            authentication.clientSecret(),
            authentication.authorityHost());
    final var scope =
        resolveScope(scopeFor(authentication.authorityHost()), authentication.entraIdScope());
    return bearerTokenSupplier(tokenCredential, scope);
  }

  /**
   * Resolves the bearer-token supplier for a managed-identity flow. No {@code authorityHost} field
   * to key off here -- Azure Public Cloud only, unless {@code entraIdScope} steps in.
   */
  public Supplier<String> bearerTokenSupplier(ManagedIdentityAuthentication authentication) {
    final var tokenCredential =
        entraIdTokenCredentialFactory.managedIdentity(authentication.clientId());
    final var scope = resolveScope(AZURE_PUBLIC_CLOUD_SCOPE, authentication.entraIdScope());
    return bearerTokenSupplier(tokenCredential, scope);
  }

  /**
   * {@link AuthenticationUtil#getBearerTokenSupplier} builds a supplier that fetches a token fresh
   * on every call, exactly what the Anthropic SDK's {@code FoundryBackend} requires ("Call
   * bearerTokenSupplier.get() each time to allow the implementation of the supplier to refresh the
   * token as necessary") -- so this never caches a token itself, relying entirely on the wrapped
   * credential's own token cache and refresh logic.
   */
  private static Supplier<String> bearerTokenSupplier(
      TokenCredential tokenCredential, String scope) {
    return AuthenticationUtil.getBearerTokenSupplier(tokenCredential, scope);
  }
}
