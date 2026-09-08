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
 * Microsoft Entra ID authentication, by wrapping the {@link TokenCredential} resolved by the
 * shared, provider-agnostic {@link EntraIdTokenCredentialFactory}. No token is cached here; the
 * wrapped {@code TokenCredential} already caches and auto-refreshes its own tokens.
 */
public class AnthropicFoundryCredentialResolver {

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory;

  public AnthropicFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    this.entraIdTokenCredentialFactory = entraIdTokenCredentialFactory;
  }

  /**
   * The authentication's own {@code entraIdScope}, when set, wins over the scope derived from
   * {@code authorityHost}.
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
   * No {@code authorityHost} field here: always Azure Public Cloud, unless {@code entraIdScope}
   * overrides it.
   */
  public Supplier<String> bearerTokenSupplier(ManagedIdentityAuthentication authentication) {
    final var tokenCredential =
        entraIdTokenCredentialFactory.managedIdentity(authentication.clientId());
    final var scope = resolveScope(AZURE_PUBLIC_CLOUD_SCOPE, authentication.entraIdScope());
    return bearerTokenSupplier(tokenCredential, scope);
  }

  private static Supplier<String> bearerTokenSupplier(
      TokenCredential tokenCredential, String scope) {
    return AuthenticationUtil.getBearerTokenSupplier(tokenCredential, scope);
  }
}
