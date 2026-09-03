/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.TokenResponse;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an OAuth2 client-credentials access token for a {@code custom}/compatible LLM backend,
 * shared by the OpenAI and Anthropic native providers (and, via migration, the MCP client). Backed
 * by the same {@link OAuthService} and {@link OAuthTokenCache} the HTTP connector uses, so tokens
 * are cached and refreshed consistently across all three consumers.
 */
public class OAuthClientCredentialsTokenResolver {

  private final OAuthService oAuthService;
  private final OAuthTokenCache oAuthTokenCache;
  private final HttpClient httpClient;

  public OAuthClientCredentialsTokenResolver(
      OAuthService oAuthService, OAuthTokenCache oAuthTokenCache, HttpClient httpClient) {
    this.oAuthService = oAuthService;
    this.oAuthTokenCache = oAuthTokenCache;
    this.httpClient = httpClient;
  }

  /**
   * Resolves an access token from the given client-credentials fields, without requiring callers to
   * construct the HTTP connector's {@link OAuthAuthentication} domain model themselves.
   */
  public String resolveAccessToken(
      String oauthTokenEndpoint,
      String clientId,
      String clientSecret,
      @Nullable String audience,
      String clientAuthentication,
      @Nullable String scopes) {
    return resolveAccessToken(
        new OAuthAuthentication(
            oauthTokenEndpoint, clientId, clientSecret, audience, clientAuthentication, scopes));
  }

  public String resolveAccessToken(OAuthAuthentication authentication) {
    return oAuthTokenCache.getOrFetch(authentication, () -> fetchToken(authentication));
  }

  private TokenResponse fetchToken(OAuthAuthentication authentication) {
    final var request = oAuthService.createOAuthRequestFrom(authentication);
    try {
      return httpClient.execute(request, oAuthService::extractTokenFromResponse).entity();
    } catch (ConnectorException e) {
      throw new ConnectorException(
          e.getErrorCode(), "OAuth client-credentials authentication failed: " + e.getMessage(), e);
    }
  }
}
