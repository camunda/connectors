/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import com.azure.identity.AzureAuthorityHosts;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Derives the Microsoft Entra ID token scope for Foundry backends. Shared between the OpenAI and
 * Anthropic Foundry credential resolvers; has no dependency on either vendor's SDK.
 */
public final class EntraIdFoundryScopeResolver {

  /**
   * Scope requested for tenants in the Azure Public Cloud, per the <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints">Microsoft
   * Foundry endpoints documentation</a>.
   */
  public static final String AZURE_PUBLIC_CLOUD_SCOPE = "https://ai.azure.com/.default";

  /**
   * Scope for tenants in the Azure US Government Cloud, per <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/concepts/foundry-azure-government">Microsoft
   * Foundry in Azure Government</a> -- the only other sovereign cloud Foundry supports today.
   */
  public static final String AZURE_GOVERNMENT_SCOPE = "https://ai.azure.us/.default";

  private EntraIdFoundryScopeResolver() {}

  /**
   * An unset/blank host, or one that isn't {@link AzureAuthorityHosts#AZURE_GOVERNMENT}, resolves
   * to Azure Public Cloud.
   */
  public static String scopeFor(@Nullable String authorityHost) {
    if (authorityHost == null || authorityHost.isBlank()) {
      return AZURE_PUBLIC_CLOUD_SCOPE;
    }

    final var isGovernmentCloud =
        normalizeAuthorityHost(authorityHost)
            .equals(normalizeAuthorityHost(AzureAuthorityHosts.AZURE_GOVERNMENT));
    return isGovernmentCloud ? AZURE_GOVERNMENT_SCOPE : AZURE_PUBLIC_CLOUD_SCOPE;
  }

  /** An explicit {@code scopeOverride}, when set, wins over the {@code derivedScope}. */
  public static String resolveScope(String derivedScope, @Nullable String scopeOverride) {
    return scopeOverride != null && !scopeOverride.isBlank() ? scopeOverride : derivedScope;
  }

  private static String normalizeAuthorityHost(String authorityHost) {
    final var lowerCased = authorityHost.strip().toLowerCase(Locale.ROOT);
    return lowerCased.endsWith("/") ? lowerCased : lowerCased + "/";
  }
}
