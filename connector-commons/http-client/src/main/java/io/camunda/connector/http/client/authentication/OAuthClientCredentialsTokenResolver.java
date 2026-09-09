/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.authentication;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an OAuth2 client-credentials access token for a {@code custom}/compatible LLM backend,
 * shared by the OpenAI and Anthropic native providers. Backed by the same {@link OAuthService} and
 * {@link OAuthTokenCache} the HTTP connector uses.
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
      final var response =
          httpClient.execute(request, oAuthService::extractTokenFromResponse).entity();
      if (response.accessToken() == null || response.accessToken().isBlank()) {
        throw new ConnectorException(
            "OAUTH_TOKEN_ERROR", "OAuth token response contains a blank access_token");
      }
      return response;
    } catch (ConnectorException e) {
      throw new ConnectorException(
          e.getErrorCode(),
          "OAuth client-credentials authentication failed: " + e.getMessage(),
          e,
          e.getErrorVariables());
    }
  }
}
