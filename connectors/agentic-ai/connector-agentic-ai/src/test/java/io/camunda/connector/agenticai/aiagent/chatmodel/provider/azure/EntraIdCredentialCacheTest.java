/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EntraIdCredentialCacheTest {

  private final EntraIdCredentialCache cache =
      new EntraIdCredentialCache(new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10)));

  @Test
  void reusesTheSameTokenCredentialForIdenticalClientCredentialsConfig() {
    final var first = cache.clientCredentials("tenant-id", "client-id", "client-secret", null);
    final var second = cache.clientCredentials("tenant-id", "client-id", "client-secret", null);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentClientCredentialsConfig() {
    final var first = cache.clientCredentials("tenant-id", "client-one", "secret-one", null);
    final var second = cache.clientCredentials("tenant-id", "client-two", "secret-two", null);

    assertThat(second).isNotSameAs(first);
  }

  @Test
  void reusesTheSameTokenCredentialForIdenticalManagedIdentityConfig() {
    final var first = cache.managedIdentity("user-assigned-id");
    final var second = cache.managedIdentity("user-assigned-id");

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentManagedIdentityConfig() {
    final var systemAssigned = cache.managedIdentity(null);
    final var userAssigned = cache.managedIdentity("user-assigned-id");

    assertThat(userAssigned).isNotSameAs(systemAssigned);
  }
}
