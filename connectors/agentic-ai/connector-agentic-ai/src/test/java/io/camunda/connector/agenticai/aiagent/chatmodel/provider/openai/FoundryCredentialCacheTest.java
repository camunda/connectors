/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.azure.core.credential.TokenCredential;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.OpenAiProperties.FoundryProperties.CredentialCacheProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FoundryCredentialCacheTest {

  @Test
  void returnsSameInstanceForTheSameKey() {
    final var cache =
        new FoundryCredentialCache(
            new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10)));

    final var first = cache.getOrCreate("same-key", () -> mock(TokenCredential.class));
    final var second = cache.getOrCreate("same-key", () -> mock(TokenCredential.class));

    assertThat(second).isSameAs(first);
  }

  @Test
  void returnsDifferentInstancesForDifferentKeys() {
    final var cache =
        new FoundryCredentialCache(
            new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10)));

    final var first = cache.getOrCreate("key-one", () -> mock(TokenCredential.class));
    final var second = cache.getOrCreate("key-two", () -> mock(TokenCredential.class));

    assertThat(second).isNotSameAs(first);
  }
}
