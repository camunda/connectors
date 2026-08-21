/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Credential-object reuse/distinctness is covered by {@code EntraIdTokenCredentialFactoryTest};
 * this class only verifies the openai-java {@code Credential} mapping per authentication variant.
 */
class FoundryCredentialResolverTest {

  private final FoundryCredentialResolver resolver =
      new FoundryCredentialResolver(
          new EntraIdTokenCredentialFactory(
              mock(AgenticAiHttpProxySupport.class),
              new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  @Test
  void resolvesApiKeyCredential() {
    final var credential =
        resolver.credential(new FoundryAuthentication.ApiKeyAuthentication("foundry-secret"));

    assertThat(credential).isNotNull();
  }

  @Test
  void resolvesClientCredentialsAndManagedIdentityCredentialsWithoutThrowing() {
    // building the wrapping Credential must not eagerly touch the network -- only calling its
    // token supplier (i.e. issuing a real request) would.
    final var clientCredentials =
        resolver.credential(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null));
    final var managedIdentity =
        resolver.credential(new FoundryAuthentication.ManagedIdentityAuthentication(null));

    assertThat(clientCredentials).isNotNull();
    assertThat(managedIdentity).isNotNull();
  }
}
