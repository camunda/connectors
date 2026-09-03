/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.bootstrap.auth;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the {@code Authorization} header for the MCP client via the shared {@link
 * OAuthClientCredentialsTokenResolver}. Note: a response without {@code expires_in} is never
 * cached, so {@link #get()} refetches a token on every call in that case.
 */
public class OAuthHeadersSupplier implements Supplier<Map<String, String>> {

  private final OAuthClientCredentialsTokenResolver tokenResolver;
  private final OAuthAuthentication config;

  public OAuthHeadersSupplier(
      OAuthClientCredentialsTokenResolver tokenResolver, OAuthAuthentication config) {
    this.tokenResolver = tokenResolver;
    this.config = config;
  }

  @Override
  public Map<String, String> get() {
    try {
      final var accessToken =
          tokenResolver.resolveAccessToken(
              config.oauthTokenEndpoint(),
              config.clientId(),
              config.clientSecret(),
              config.audience(),
              config.clientAuthentication().oauthConstant(),
              config.scopes());
      return Map.of("Authorization", "Bearer " + accessToken);
    } catch (ConnectorException e) {
      final var responseBody = extractResponseBody(e.getErrorVariables());
      throw new ConnectorException(
          e.getErrorCode(),
          "MCP client authentication failed: %s%s"
              .formatted(e.getMessage(), responseBody != null ? " - " + responseBody : ""),
          e,
          e.getErrorVariables());
    }
  }

  private static @Nullable Object extractResponseBody(
      @Nullable Map<String, Object> errorVariables) {
    if (errorVariables == null) {
      return null;
    }
    final var response = errorVariables.get("response");
    return response instanceof Map<?, ?> responseMap ? responseMap.get("body") : null;
  }
}
