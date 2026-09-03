/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SlowTest
@WireMockTest
class OAuthClientCredentialsTokenResolverTest {

  private OAuthClientCredentialsTokenResolver resolver;
  private String tokenEndpoint;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    OAuthService oAuthService = new OAuthService();
    HttpClient httpClient = new CustomApacheHttpClient();
    resolver =
        new OAuthClientCredentialsTokenResolver(
            oAuthService, new CaffeineOAuthTokenCache(), httpClient);
    tokenEndpoint = wmRuntimeInfo.getHttpBaseUrl() + "/oauth/token";
  }

  @Test
  void shouldFetchAccessTokenUsingBasicAuthHeader() {
    stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "test-access-token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """)));

    final var auth =
        new OAuthAuthentication(
            tokenEndpoint,
            "my-client-id",
            "my-client-secret",
            "https://api.example.com",
            OAuthConstants.BASIC_AUTH_HEADER,
            "openid my-scope");

    final var token = resolver.resolveAccessToken(auth);

    assertThat(token).isEqualTo("test-access-token");

    verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withBasicAuth(new BasicCredentials("my-client-id", "my-client-secret"))
            .withFormParam("grant_type", equalTo("client_credentials"))
            .withFormParam("scope", equalTo("openid my-scope"))
            .withFormParam("audience", equalTo("https://api.example.com")));
  }

  @Test
  void shouldCacheTokenAndReuseIt() {
    stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "cached-token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """)));

    final var auth =
        new OAuthAuthentication(
            tokenEndpoint,
            "my-client-id",
            "my-client-secret",
            null,
            OAuthConstants.BASIC_AUTH_HEADER,
            null);

    final var token1 = resolver.resolveAccessToken(auth);
    final var token2 = resolver.resolveAccessToken(auth);

    assertThat(token1).isEqualTo("cached-token");
    assertThat(token2).isEqualTo(token1);

    verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void shouldThrowConnectorExceptionOnHttpFailure() {
    stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": "invalid_client",
                          "error_description": "Invalid client credentials"
                        }
                        """)));

    final var auth =
        new OAuthAuthentication(
            tokenEndpoint,
            "bad-client",
            "bad-secret",
            null,
            OAuthConstants.BASIC_AUTH_HEADER,
            null);

    assertThatThrownBy(() -> resolver.resolveAccessToken(auth))
        .isInstanceOf(ConnectorException.class);
  }
}
