/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AzureAuthorityHosts;
import io.camunda.connector.agenticai.aiagent.model.request.v2.FoundryAuthentication.ClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.FoundryAuthentication.ManagedIdentityAuthentication;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the bearer-token {@link Supplier} for the Microsoft Entra ID {@code foundry}
 * authentication variants by wrapping the {@link TokenCredential} resolved by {@link
 * EntraIdTokenCredentialFactory}, together with the Entra ID token scope to request. Returns a
 * plain {@link Supplier}, so it stays independent of any model vendor's SDK.
 *
 * <p>No token is cached here: the supplier asks the credential for a token on every request,
 * relying entirely on the wrapped credential's own token cache and refresh logic. The token is read
 * straight off the credential, so invoking the supplier issues no HTTP request of its own beyond
 * the credential's token exchange.
 */
public class FoundryCredentialResolver {

  /**
   * Scope requested for tenants in the Azure Public Cloud, per the <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints">Microsoft
   * Foundry endpoints documentation</a>.
   */
  private static final String AZURE_PUBLIC_CLOUD_SCOPE = "https://ai.azure.com/.default";

  /**
   * Scope for tenants in the Azure US Government Cloud, per <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/concepts/foundry-azure-government">Microsoft
   * Foundry in Azure Government</a> -- the only other sovereign cloud Foundry supports today.
   */
  private static final String AZURE_GOVERNMENT_SCOPE = "https://ai.azure.us/.default";

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory;

  public FoundryCredentialResolver(EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    this.entraIdTokenCredentialFactory = entraIdTokenCredentialFactory;
  }

  /**
   * The authentication's own {@code entraIdScope}, when set, wins over the scope derived from
   * {@code authorityHost}. {@code timeout} bounds the Entra ID token exchange the supplier
   * performs; {@code null} leaves the SDK default in place.
   */
  public Supplier<String> bearerTokenSupplier(
      ClientCredentialsAuthentication authentication, @Nullable Duration timeout) {
    final var tokenCredential =
        entraIdTokenCredentialFactory.clientCredentials(
            authentication.tenantId(),
            authentication.clientId(),
            authentication.clientSecret(),
            authentication.authorityHost(),
            timeout);
    return tokenSupplier(
        tokenCredential, scopeFor(authentication.authorityHost(), authentication.entraIdScope()));
  }

  /**
   * No {@code authorityHost} field here: always Azure Public Cloud, unless {@code entraIdScope}
   * overrides it.
   */
  public Supplier<String> bearerTokenSupplier(
      ManagedIdentityAuthentication authentication, @Nullable Duration timeout) {
    final var tokenCredential =
        entraIdTokenCredentialFactory.managedIdentity(authentication.clientId(), timeout);
    return tokenSupplier(tokenCredential, scopeFor(null, authentication.entraIdScope()));
  }

  /**
   * Reads the token off the credential directly. The azure-identity {@code
   * AuthenticationUtil.getBearerTokenSupplier} helper is deliberately not used: it obtains the
   * token by sending a throwaway HTTP request to {@code https://www.example.com} and reading the
   * {@code Authorization} header back off it, which would put an outbound call to an unrelated host
   * on every LLM request and bypass the credential's own proxy configuration.
   */
  private static Supplier<String> tokenSupplier(TokenCredential tokenCredential, String scope) {
    return () ->
        tokenCredential.getTokenSync(new TokenRequestContext().addScopes(scope)).getToken();
  }

  private static String scopeFor(@Nullable String authorityHost, @Nullable String scopeOverride) {
    if (scopeOverride != null && !scopeOverride.isBlank()) {
      return scopeOverride;
    }
    if (authorityHost == null || authorityHost.isBlank()) {
      return AZURE_PUBLIC_CLOUD_SCOPE;
    }

    final var isGovernmentCloud =
        normalizeAuthorityHost(authorityHost)
            .equals(normalizeAuthorityHost(AzureAuthorityHosts.AZURE_GOVERNMENT));
    return isGovernmentCloud ? AZURE_GOVERNMENT_SCOPE : AZURE_PUBLIC_CLOUD_SCOPE;
  }

  private static String normalizeAuthorityHost(String authorityHost) {
    final var lowerCased = authorityHost.strip().toLowerCase(Locale.ROOT);
    return lowerCased.endsWith("/") ? lowerCased : lowerCased + "/";
  }
}
