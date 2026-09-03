/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth;

import com.anthropic.core.http.Interceptor;
import org.jspecify.annotations.Nullable;

/**
 * Injects a resolved bearer token per-request via {@link Interceptor#syncOnly}. Synchronous only:
 * this provider's chat model never calls the anthropic-java async client.
 */
public final class OAuthBearerTokenInterceptor {

  private OAuthBearerTokenInterceptor() {}

  public static Interceptor create(
      OAuthClientCredentialsTokenResolver tokenResolver,
      String oauthTokenEndpoint,
      String clientId,
      String clientSecret,
      @Nullable String audience,
      String clientAuthentication,
      @Nullable String scopes) {
    return Interceptor.syncOnly(
        (client, request, requestOptions) -> {
          final var accessToken =
              tokenResolver.resolveAccessToken(
                  oauthTokenEndpoint,
                  clientId,
                  clientSecret,
                  audience,
                  clientAuthentication,
                  scopes);
          final var authorizedRequest =
              request.toBuilder().replaceHeaders("Authorization", "Bearer " + accessToken).build();
          return client.execute(authorizedRequest, requestOptions);
        });
  }
}
