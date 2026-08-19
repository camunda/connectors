/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.OpenAiProperties.FoundryProperties.CredentialCacheProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FoundryCredentialResolverTest {

  private final FoundryCredentialResolver resolver =
      new FoundryCredentialResolver(
          new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10)));

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

  @Test
  void reusesTheSameTokenCredentialForIdenticalClientCredentialsConfig() {
    final var auth =
        new FoundryAuthentication.ClientCredentialsAuthentication(
            "client-id", "client-secret", "tenant-id", null);

    final var first = resolver.tokenCredential(auth);
    final var second = resolver.tokenCredential(auth);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentClientCredentialsConfig() {
    final var first =
        resolver.tokenCredential(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-one", "secret-one", "tenant-id", null));
    final var second =
        resolver.tokenCredential(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-two", "secret-two", "tenant-id", null));

    assertThat(second).isNotSameAs(first);
  }

  @Test
  void reusesTheSameTokenCredentialForIdenticalManagedIdentityConfig() {
    final var auth = new FoundryAuthentication.ManagedIdentityAuthentication("user-assigned-id");

    final var first = resolver.tokenCredential(auth);
    final var second = resolver.tokenCredential(auth);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentManagedIdentityConfig() {
    final var systemAssigned =
        resolver.tokenCredential(new FoundryAuthentication.ManagedIdentityAuthentication(null));
    final var userAssigned =
        resolver.tokenCredential(
            new FoundryAuthentication.ManagedIdentityAuthentication("user-assigned-id"));

    assertThat(userAssigned).isNotSameAs(systemAssigned);
  }
}
