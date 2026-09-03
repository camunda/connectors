/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpMethod;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.Interceptor;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthBearerTokenInterceptorTest {

  @Mock private OAuthClientCredentialsTokenResolver tokenResolver;
  @Mock private HttpClient delegate;
  @Mock private HttpResponse response;

  private Interceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor =
        OAuthBearerTokenInterceptor.create(
            tokenResolver,
            "https://auth.example.com/oauth/token",
            "my-client-id",
            "my-client-secret",
            null,
            OAuthConstants.BASIC_AUTH_HEADER,
            null);
  }

  @Test
  void addsBearerAuthorizationHeaderResolvedPerRequest() {
    when(tokenResolver.resolveAccessToken(any(), any(), any(), any(), any(), any()))
        .thenReturn("resolved-token");
    when(delegate.execute(any(), any())).thenReturn(response);

    final var wrapped = interceptor.intercept(delegate);
    final var request =
        HttpRequest.builder().method(HttpMethod.GET).baseUrl("https://api.example.com").build();

    wrapped.execute(request);

    final var captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(delegate).execute(captor.capture(), any());
    assertThat(captor.getValue().headers().values("Authorization"))
        .containsExactly("Bearer resolved-token");
  }

  @Test
  void resolvesTokenAgainOnEachRequest() {
    when(tokenResolver.resolveAccessToken(any(), any(), any(), any(), any(), any()))
        .thenReturn("token-1", "token-2");
    when(delegate.execute(any(), any())).thenReturn(response);

    final var wrapped = interceptor.intercept(delegate);
    final var request =
        HttpRequest.builder().method(HttpMethod.GET).baseUrl("https://api.example.com").build();

    wrapped.execute(request);
    wrapped.execute(request);

    verify(tokenResolver, times(2))
        .resolveAccessToken(
            "https://auth.example.com/oauth/token",
            "my-client-id",
            "my-client-secret",
            null,
            OAuthConstants.BASIC_AUTH_HEADER,
            null);
  }

  @Test
  void doesNotSupportAsyncExecution() {
    final var wrapped = interceptor.intercept(delegate);
    final var request =
        HttpRequest.builder().method(HttpMethod.GET).baseUrl("https://api.example.com").build();

    assertThatThrownBy(() -> wrapped.executeAsync(request))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
