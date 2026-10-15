/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AuthenticationUtil;
import io.camunda.connector.agenticai.aiagent.model.request.v2.FoundryAuthentication;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * Credential-object reuse/distinctness is covered by {@code EntraIdTokenCredentialFactoryTest};
 * this class only verifies the bearer-token-supplier and scope resolution per authentication
 * variant.
 */
class FoundryCredentialResolverTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(7);

  private final EntraIdTokenCredentialFactory entraIdTokenCredentialFactory =
      mock(EntraIdTokenCredentialFactory.class);
  private final TokenCredential tokenCredential = mock(TokenCredential.class);
  private final FoundryCredentialResolver resolver =
      new FoundryCredentialResolver(entraIdTokenCredentialFactory);

  @BeforeEach
  void setUp() {
    when(entraIdTokenCredentialFactory.clientCredentials(any(), any(), any(), any(), any()))
        .thenReturn(tokenCredential);
    when(entraIdTokenCredentialFactory.managedIdentity(any(), any())).thenReturn(tokenCredential);
    when(tokenCredential.getTokenSync(any()))
        .thenReturn(new AccessToken("test-token", OffsetDateTime.MAX));
  }

  @Test
  void suppliesTheTokenFromTheCredentialItself() {
    final var supplier =
        resolver.bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null, null),
            TIMEOUT);

    assertThat(supplier.get()).isEqualTo("test-token");
  }

  @Test
  void buildingTheSupplierDoesNotRequestAToken() {
    resolver.bearerTokenSupplier(
        new FoundryAuthentication.ClientCredentialsAuthentication(
            "client-id", "client-secret", "tenant-id", null, null),
        TIMEOUT);

    verify(tokenCredential, org.mockito.Mockito.never()).getTokenSync(any());
  }

  @Test
  void passesTheCredentialConfigurationToTheFactory() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id",
                "client-secret",
                "tenant-id",
                "https://login.microsoftonline.us/",
                null),
            TIMEOUT)
        .get();

    verify(entraIdTokenCredentialFactory)
        .clientCredentials(
            "tenant-id",
            "client-id",
            "client-secret",
            "https://login.microsoftonline.us/",
            TIMEOUT);
  }

  @Test
  void passesTheManagedIdentityClientIdToTheFactory() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ManagedIdentityAuthentication("mi-id", null), TIMEOUT)
        .get();

    verify(entraIdTokenCredentialFactory).managedIdentity("mi-id", TIMEOUT);
  }

  @Test
  void requestsTheFoundryScopeForClientCredentials() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null, null),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.com/.default");
  }

  @Test
  void requestsTheFoundryScopeForManagedIdentity() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ManagedIdentityAuthentication(null, null), TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.com/.default");
  }

  @Test
  void requestsTheGovernmentCloudScopeForMatchingAuthorityHost() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id",
                "client-secret",
                "tenant-id",
                "https://login.microsoftonline.us/",
                null),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.us/.default");
  }

  @Test
  void requestsThePublicCloudScopeForUnknownAuthorityHost() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id",
                "client-secret",
                "tenant-id",
                "https://login.someprivatecloud.example/",
                null),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.com/.default");
  }

  @Test
  void scopeOverrideWinsOverDerivedScopeForClientCredentials() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id",
                "client-secret",
                "tenant-id",
                "https://login.microsoftonline.us/",
                "https://custom.scope/.default"),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://custom.scope/.default");
  }

  @Test
  void scopeOverrideWinsOverDefaultScopeForManagedIdentity() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ManagedIdentityAuthentication(
                null, "https://ai.azure.us/.default"),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.us/.default");
  }

  @Test
  void blankScopeOverrideIsIgnored() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null, "   "),
            TIMEOUT)
        .get();

    assertThat(requestedScopes()).containsExactly("https://ai.azure.com/.default");
  }

  /**
   * {@code AuthenticationUtil.getBearerTokenSupplier} obtains the token by sending a throwaway HTTP
   * request to {@code https://www.example.com} through a pipeline and reading the {@code
   * Authorization} header back off it. That is an outbound call to an unrelated third-party host on
   * every LLM request, and it bypasses the proxy the credential is configured with, so the token
   * must come from the credential directly instead.
   */
  @Test
  void doesNotAcquireTheTokenThroughAnHttpRequest() {
    try (MockedStatic<AuthenticationUtil> authenticationUtil =
        mockStatic(AuthenticationUtil.class)) {
      resolver
          .bearerTokenSupplier(
              new FoundryAuthentication.ClientCredentialsAuthentication(
                  "client-id", "client-secret", "tenant-id", null, null),
              TIMEOUT)
          .get();

      authenticationUtil.verifyNoInteractions();
    }
  }

  @Test
  void managedIdentityResolvesTheSystemAssignedIdentityForABlankClientId() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ManagedIdentityAuthentication(null, null), TIMEOUT)
        .get();

    verify(entraIdTokenCredentialFactory).managedIdentity(isNull(), eq(TIMEOUT));
  }

  @Test
  void passesAnAbsentTimeoutThroughUnchanged() {
    resolver
        .bearerTokenSupplier(
            new FoundryAuthentication.ClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null, null),
            null)
        .get();

    verify(entraIdTokenCredentialFactory)
        .clientCredentials("tenant-id", "client-id", "client-secret", null, null);
  }

  private java.util.List<String> requestedScopes() {
    final var request = ArgumentCaptor.forClass(TokenRequestContext.class);
    verify(tokenCredential).getTokenSync(request.capture());
    return request.getValue().getScopes();
  }
}
