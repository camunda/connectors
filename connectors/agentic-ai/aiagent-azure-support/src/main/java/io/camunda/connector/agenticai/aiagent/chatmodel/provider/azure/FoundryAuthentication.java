/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import org.jspecify.annotations.Nullable;

/** How a Microsoft Foundry endpoint authenticates the calls. */
public sealed interface FoundryAuthentication {

  record ApiKey(String apiKey) implements FoundryAuthentication {

    @Override
    public String toString() {
      return "ApiKey{apiKey=[REDACTED]}";
    }
  }

  /**
   * Microsoft Entra ID client credentials.
   *
   * @param authorityHost the Entra ID authority host, or {@code null} for the public cloud one
   * @param entraIdScope the token scope to request, or {@code null} for the default of the cloud
   */
  record ClientCredentials(
      String clientId,
      String clientSecret,
      String tenantId,
      @Nullable String authorityHost,
      @Nullable String entraIdScope)
      implements FoundryAuthentication {

    @Override
    public String toString() {
      return "ClientCredentials{clientId=%s, clientSecret=[REDACTED], tenantId=%s, authorityHost=%s, entraIdScope=%s}"
          .formatted(clientId, tenantId, authorityHost, entraIdScope);
    }
  }

  /**
   * Microsoft Entra ID managed identity.
   *
   * @param clientId the client ID of a user-assigned identity, or {@code null} for the
   *     system-assigned one
   * @param entraIdScope the token scope to request, or {@code null} for the default of the cloud
   */
  record ManagedIdentity(@Nullable String clientId, @Nullable String entraIdScope)
      implements FoundryAuthentication {}
}
