/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.Interceptor;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.util.concurrent.CompletableFuture;

/**
 * Anthropic-side half of the shared OAuth2 client-credentials mechanism: the anthropic-java SDK has
 * no public dynamic-credential hook (unlike openai-java's {@code Credential}), so a bearer token is
 * instead injected per-request via the SDK's supported {@link Interceptor} hook, backed by the same
 * {@link OAuthClientCredentialsTokenResolver} the OpenAI provider uses.
 */
public class OAuthBearerTokenInterceptor implements Interceptor {

  private final OAuthClientCredentialsTokenResolver tokenResolver;
  private final OAuthAuthentication authentication;

  public OAuthBearerTokenInterceptor(
      OAuthClientCredentialsTokenResolver tokenResolver, OAuthAuthentication authentication) {
    this.tokenResolver = tokenResolver;
    this.authentication = authentication;
  }

  @Override
  public HttpClient intercept(HttpClient httpClient) {
    return new HttpClient() {
      @Override
      public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        return httpClient.execute(withBearerToken(request), requestOptions);
      }

      @Override
      public CompletableFuture<HttpResponse> executeAsync(
          HttpRequest request, RequestOptions requestOptions) {
        return httpClient.executeAsync(withBearerToken(request), requestOptions);
      }

      @Override
      public void close() {
        httpClient.close();
      }
    };
  }

  private HttpRequest withBearerToken(HttpRequest request) {
    final var accessToken = tokenResolver.resolveAccessToken(authentication);
    return request.toBuilder().putHeader("Authorization", "Bearer " + accessToken).build();
  }
}
