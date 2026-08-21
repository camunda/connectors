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
    final var first = factory.clientCredentials("tenant-id", "client-id", "client-secret", null);
    final var second = factory.clientCredentials("tenant-id", "client-id", "client-secret", null);

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentClientCredentialsConfig() {
    final var first = factory.clientCredentials("tenant-id", "client-one", "secret-one", null);
    final var second = factory.clientCredentials("tenant-id", "client-two", "secret-two", null);

    assertThat(second).isNotSameAs(first);
  }

  @Test
  void reusesTheSameTokenCredentialForIdenticalManagedIdentityConfig() {
    final var first = factory.managedIdentity("user-assigned-id");
    final var second = factory.managedIdentity("user-assigned-id");

    assertThat(second).isSameAs(first);
  }

  @Test
  void buildsDistinctTokenCredentialsForDifferentManagedIdentityConfig() {
    final var systemAssigned = factory.managedIdentity(null);
    final var userAssigned = factory.managedIdentity("user-assigned-id");

    assertThat(userAssigned).isNotSameAs(systemAssigned);
  }

  @Test
  void appliesConfiguredProxyToClientCredentialsTokenExchange() {
    final var proxyOptions =
        new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
    when(httpProxySupport.azureProxyOptions(ProxyConfiguration.SCHEME_HTTPS))
        .thenReturn(Optional.of(proxyOptions));

    factory.clientCredentials("tenant-id", "client-id", "client-secret", null);

    verify(httpProxySupport).azureProxyOptions(ProxyConfiguration.SCHEME_HTTPS);
  }

  @Test
  void doesNotRouteManagedIdentityTokenExchangeThroughTheProxy() {
    // IMDS lives at a link-local address (or an environment-provided local sidecar endpoint),
    // neither of which is reachable via an internet-facing egress proxy.
    factory.managedIdentity(null);

    verifyNoInteractions(httpProxySupport);
  }
}
