/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.core.http.ProxyOptions;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EntraIdTokenCredentialFactoryTest {

  private final AgenticAiHttpProxySupport httpProxySupport = mock(AgenticAiHttpProxySupport.class);

  private final EntraIdTokenCredentialFactory factory =
      new EntraIdTokenCredentialFactory(
          httpProxySupport, new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10)));

  @Test
  void reusesTheSameTokenCredentialForIdenticalClientCredentialsConfig() {
    final var first =
        factory.clientCredentials("tenant-id", "client-id", "client-secret", null, null);
    final var second =
        factory.clientCredentials("tenant-id", "client-id", "client-secret", null, null);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentClientCredentialsConfig() {
    final var first =
        factory.clientCredentials("tenant-id", "client-one", "secret-one", null, null);
    final var second =
        factory.clientCredentials("tenant-id", "client-two", "secret-two", null, null);

    assertThat(second).isNotSameAs(first);
  }

  @Test
  void reusesTheSameTokenCredentialForIdenticalManagedIdentityConfig() {
    final var first = factory.managedIdentity("user-assigned-id", null);
    final var second = factory.managedIdentity("user-assigned-id", null);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentManagedIdentityConfig() {
    final var systemAssigned = factory.managedIdentity(null, null);
    final var userAssigned = factory.managedIdentity("user-assigned-id", null);

    assertThat(userAssigned).isNotSameAs(systemAssigned);
  }

  @Test
  void appliesConfiguredProxyToClientCredentialsTokenExchange() {
    final var proxyOptions =
        new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
    when(httpProxySupport.azureProxyOptions(ProxyConfiguration.SCHEME_HTTPS))
        .thenReturn(Optional.of(proxyOptions));

    factory.clientCredentials("tenant-id", "client-id", "client-secret", null, null);

    verify(httpProxySupport).azureProxyOptions(ProxyConfiguration.SCHEME_HTTPS);
  }

  @Test
  void doesNotRouteManagedIdentityTokenExchangeThroughTheProxy() {
    // IMDS lives at a link-local address (or an environment-provided local sidecar endpoint),
    // neither of which is reachable via an internet-facing egress proxy.
    factory.managedIdentity(null, null);

    verifyNoInteractions(httpProxySupport);
  }

  @Test
  void reusesTheSameTokenCredentialForAnIdenticalClientCredentialsTimeout() {
    final var first =
        factory.clientCredentials(
            "tenant-id", "client-id", "client-secret", null, Duration.ofSeconds(5));
    final var second =
        factory.clientCredentials(
            "tenant-id", "client-id", "client-secret", null, Duration.ofSeconds(5));

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentClientCredentialsTimeouts() {
    // the timeout is baked into the credential's HTTP client, so two otherwise identical
    // configurations must not share one cached credential.
    final var shortTimeout =
        factory.clientCredentials(
            "tenant-id", "client-id", "client-secret", null, Duration.ofSeconds(5));
    final var longTimeout =
        factory.clientCredentials(
            "tenant-id", "client-id", "client-secret", null, Duration.ofSeconds(30));

    assertThat(longTimeout).isNotSameAs(shortTimeout);
  }

  @Test
  void buildsDistinctTokenCredentialsWhenOnlyOneClientCredentialsConfigHasATimeout() {
    final var untimed =
        factory.clientCredentials("tenant-id", "client-id", "client-secret", null, null);
    final var timed =
        factory.clientCredentials(
            "tenant-id", "client-id", "client-secret", null, Duration.ofSeconds(5));

    assertThat(timed).isNotSameAs(untimed);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentManagedIdentityTimeouts() {
    final var shortTimeout = factory.managedIdentity("user-assigned-id", Duration.ofSeconds(5));
    final var longTimeout = factory.managedIdentity("user-assigned-id", Duration.ofSeconds(30));

    assertThat(longTimeout).isNotSameAs(shortTimeout);
  }

  @Test
  void doesNotRouteATimedManagedIdentityTokenExchangeThroughTheProxy() {
    factory.managedIdentity(null, Duration.ofSeconds(5));

    verifyNoInteractions(httpProxySupport);
  }
}
