/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.azure.identity.AuthenticationUtil;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicFoundryAuthentication.ClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicFoundryAuthentication.ManagedIdentityAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Credential-object reuse/distinctness is covered by {@code EntraIdTokenCredentialFactoryTest};
 * this class only verifies the bearer-token-supplier mapping per authentication variant, mirroring
 * {@code OpenAiFoundryCredentialResolverTest}.
 */
class AnthropicFoundryCredentialResolverTest {

  private final AnthropicFoundryCredentialResolver resolver =
      new AnthropicFoundryCredentialResolver(
          new EntraIdTokenCredentialFactory(
              mock(AgenticAiHttpProxySupport.class),
              new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  @Test
  void resolvesClientCredentialsAndManagedIdentitySuppliersWithoutThrowing() {
    // building the supplier must not eagerly touch the network -- only calling it (i.e. issuing a
    // real request) would.
    final var clientCredentials =
        resolver.bearerTokenSupplier(
            new ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null, null));
    final var managedIdentity =
        resolver.bearerTokenSupplier(new ManagedIdentityAuthentication(null, null));

    assertThat(clientCredentials).isNotNull();
    assertThat(managedIdentity).isNotNull();
  }

  @Test
  void requestsTheFoundryScopeForClientCredentials() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);
      resolver.bearerTokenSupplier(
          new ClientCredentialsAuthentication(
              "client-id", "client-secret", "tenant-id", null, null));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(
                  any(), eq("https://ai.azure.com/.default")));
    }
  }

  @Test
  void requestsTheFoundryScopeForManagedIdentity() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);
      resolver.bearerTokenSupplier(new ManagedIdentityAuthentication(null, null));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(
                  any(), eq("https://ai.azure.com/.default")));
    }
  }

  @Test
  void requestsTheGovernmentCloudScopeForMatchingAuthorityHost() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);

      resolver.bearerTokenSupplier(
          new ClientCredentialsAuthentication(
              "client-id",
              "client-secret",
              "tenant-id",
              "https://login.microsoftonline.us/",
              null));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(any(), eq("https://ai.azure.us/.default")));
    }
  }

  @Test
  void requestsThePublicCloudScopeForUnknownAuthorityHost() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);

      resolver.bearerTokenSupplier(
          new ClientCredentialsAuthentication(
              "client-id",
              "client-secret",
              "tenant-id",
              "https://login.someprivatecloud.example/",
              null));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(
                  any(), eq("https://ai.azure.com/.default")));
    }
  }

  @Test
  void scopeOverrideWinsOverDerivedScopeForClientCredentials() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);

      resolver.bearerTokenSupplier(
          new ClientCredentialsAuthentication(
              "client-id",
              "client-secret",
              "tenant-id",
              "https://login.microsoftonline.us/",
              "https://custom.scope/.default"));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(
                  any(), eq("https://custom.scope/.default")));
    }
  }

  @Test
  void scopeOverrideWinsOverDefaultScopeForManagedIdentity() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);

      resolver.bearerTokenSupplier(
          new ManagedIdentityAuthentication(null, "https://ai.azure.us/.default"));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(any(), eq("https://ai.azure.us/.default")));
    }
  }

  @Test
  void blankScopeOverrideIsIgnored() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      final Supplier<String> tokenSupplier = () -> "test-token";
      authenticationUtil
          .when(() -> AuthenticationUtil.getBearerTokenSupplier(any(), any()))
          .thenReturn(tokenSupplier);

      resolver.bearerTokenSupplier(
          new ClientCredentialsAuthentication(
              "client-id", "client-secret", "tenant-id", null, "   "));

      authenticationUtil.verify(
          () ->
              AuthenticationUtil.getBearerTokenSupplier(
                  any(), eq("https://ai.azure.com/.default")));
    }
  }
}
