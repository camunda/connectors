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
class OpenAiFoundryCredentialResolverTest {

  private final OpenAiFoundryCredentialResolver resolver =
      new OpenAiFoundryCredentialResolver(
          new EntraIdTokenCredentialFactory(
              mock(AgenticAiHttpProxySupport.class),
              new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  @Test
  void resolvesApiKeyCredential() {
    final var credential =
        resolver.credential(
            "https://my-resource.openai.azure.com",
            new FoundryAuthentication.ApiKeyAuthentication("foundry-secret"));

    assertThat(credential).isNotNull();
  }

  @Test
  void resolvesClientCredentialsAndManagedIdentityCredentialsWithoutThrowing() {
    // building the wrapping Credential must not eagerly touch the network -- only calling its
    // token supplier (i.e. issuing a real request) would.
    final var clientCredentials =
        resolver.credential(
            "https://my-resource.openai.azure.com",
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null));
    final var managedIdentity =
        resolver.credential(
            "https://my-resource.openai.azure.com",
            new FoundryAuthentication.ManagedIdentityAuthentication(null));

    assertThat(clientCredentials).isNotNull();
    assertThat(managedIdentity).isNotNull();
  }

  @Test
  void resolvesClassicAzureOpenAiScope() {
    assertThat(OpenAiFoundryCredentialResolver.scopeFor("https://my-resource.openai.azure.com"))
        .isEqualTo("https://cognitiveservices.azure.com/.default");
  }

  @Test
  void resolvesUnifiedFoundryScope() {
    assertThat(
            OpenAiFoundryCredentialResolver.scopeFor("https://my-resource.services.ai.azure.com"))
        .isEqualTo("https://ai.azure.com/.default");
  }

  @Test
  void defaultsToClassicScopeForNonFoundryHost() {
    assertThat(OpenAiFoundryCredentialResolver.scopeFor("https://example.com"))
        .isEqualTo("https://cognitiveservices.azure.com/.default");
  }

  @Test
  void resolvesUnifiedFoundryScopeCaseInsensitively() {
    assertThat(
            OpenAiFoundryCredentialResolver.scopeFor("https://MY-RESOURCE.SERVICES.AI.AZURE.COM"))
        .isEqualTo("https://ai.azure.com/.default");
  }
}
