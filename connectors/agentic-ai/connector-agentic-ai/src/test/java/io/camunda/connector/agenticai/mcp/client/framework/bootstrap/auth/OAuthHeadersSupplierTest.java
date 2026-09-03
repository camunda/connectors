/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.bootstrap.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication.ClientAuthenticationMethod;
import io.camunda.connector.api.error.ConnectorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthHeadersSupplierTest {

  @Mock private OAuthClientCredentialsTokenResolver tokenResolver;

  private OAuthAuthentication config;

  @BeforeEach
  void setUp() {
    config =
        new OAuthAuthentication(
            "https://auth.example.com/oauth/token",
            "my-client-id",
            "my-client-secret",
            "https://api.example.com",
            ClientAuthenticationMethod.BASIC_AUTH_HEADER,
            "openid my-scope");
  }

  @Test
  void returnsBearerAuthorizationHeaderFromResolvedToken() {
    when(tokenResolver.resolveAccessToken(any())).thenReturn("resolved-access-token");

    final var supplier = new OAuthHeadersSupplier(tokenResolver, config);

    assertThat(supplier.get()).containsEntry("Authorization", "Bearer resolved-access-token");
  }

  @Test
  void mapsConfigurationToHttpClientOAuthAuthenticationForTheResolver() {
    when(tokenResolver.resolveAccessToken(any())).thenReturn("token");

    new OAuthHeadersSupplier(tokenResolver, config).get();

    verify(tokenResolver)
        .resolveAccessToken(
            new io.camunda.connector.http.client.model.auth.OAuthAuthentication(
                "https://auth.example.com/oauth/token",
                "my-client-id",
                "my-client-secret",
                "https://api.example.com",
                io.camunda.connector.http.client.authentication.OAuthConstants.BASIC_AUTH_HEADER,
                "openid my-scope"));
  }

  @Test
  void wrapsResolverFailureWithMcpContext() {
    when(tokenResolver.resolveAccessToken(any()))
        .thenThrow(new ConnectorException("OAUTH_TOKEN_ERROR", "token endpoint returned 401"));

    final var supplier = new OAuthHeadersSupplier(tokenResolver, config);

    assertThatThrownBy(supplier::get)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("MCP client authentication failed: token endpoint returned 401");
  }
}
