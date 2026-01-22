/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.bootstrap.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication.ClientAuthenticationMethod;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@WireMockTest
class OAuthHeadersSupplierTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private OAuthService oAuthService;
  private HttpClient httpClient;
  private String tokenEndpoint;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    oAuthService = new OAuthService();
    httpClient = new CustomApacheHttpClient();
    tokenEndpoint = wmRuntimeInfo.getHttpBaseUrl() + "/oauth/token";
  }

  @Nested
  class BasicAuthHeaderMode {

    @Test
    void shouldSendBasicAuthHeaderWithClientCredentials() {
      // given
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              "openid my-scope");

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when
      final var headers = supplier.get();

      // then
      assertThat(headers).containsEntry("Authorization", "Bearer test-access-token");

      verify(
          postRequestedFor(urlEqualTo("/oauth/token"))
              .withBasicAuth(new BasicCredentials("my-client-id", "my-client-secret"))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("scope", equalTo("openid my-scope"))
              .withFormParam("audience", equalTo("https://api.example.com")));
    }

    @Test
    void shouldWorkWithoutOptionalParameters() {
      // given
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
                        "expires_in": 7200
                      }
                      """)));

      final var auth =
          new OAuthAuthentication(
              tokenEndpoint,
              "my-client-id",
              "my-client-secret",
              null, // no audience
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null); // no scopes

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when
      final var headers = supplier.get();

      // then
      assertThat(headers).containsEntry("Authorization", "Bearer test-access-token");

      verify(
          postRequestedFor(urlEqualTo("/oauth/token"))
              .withBasicAuth(new BasicCredentials("my-client-id", "my-client-secret"))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withoutFormParam("scope")
              .withFormParam("audience", equalTo("")));
    }
  }

  @Nested
  class CredentialsBodyMode {

    @Test
    void shouldSendCredentialsInRequestBody() {
      // given
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                      {
                        "access_token": "body-auth-token",
                        "token_type": "Bearer",
                        "expires_in": 1800
                      }
                      """)));

      final var auth =
          new OAuthAuthentication(
              tokenEndpoint,
              "body-client-id",
              "body-client-secret",
              null,
              ClientAuthenticationMethod.CREDENTIALS_BODY,
              "openid my-scope");

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when
      final var headers = supplier.get();

      // then
      assertThat(headers).containsEntry("Authorization", "Bearer body-auth-token");

      verify(
          postRequestedFor(urlEqualTo("/oauth/token"))
              .withoutHeader("Authorization")
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("scope", equalTo("openid my-scope"))
              .withFormParam("audience", equalTo(""))
              .withFormParam("client_id", equalTo("body-client-id"))
              .withFormParam("client_secret", equalTo("body-client-secret")));
    }
  }

  @Nested
  class TokenCachingAndRefresh {

    private static final Instant BASE_INSTANT = Instant.parse("2025-01-01T10:00:00Z");

    @Test
    void shouldCacheTokenAndReuseIt() {
      // given
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when
      final var headers1 = supplier.get();
      final var headers2 = supplier.get();
      final var headers3 = supplier.get();

      // then
      assertThat(headers1).containsEntry("Authorization", "Bearer cached-token");
      assertThat(headers2).isEqualTo(headers1);
      assertThat(headers3).isEqualTo(headers1);

      // verify token was fetched only once
      verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    void shouldFetchTokenOnlyOnceWhenRequestedConcurrently() throws Exception {
      // given
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when
      final int threads = 3;
      try (final var executor = Executors.newFixedThreadPool(threads)) {
        final var futures =
            IntStream.range(0, threads)
                .mapToObj(i -> CompletableFuture.supplyAsync(supplier, executor))
                .toList();

        final var headers1 = futures.get(0).get();
        final var headers2 = futures.get(1).get();
        final var headers3 = futures.get(2).get();
        executor.shutdownNow();

        // then (unchanged assertions)
        assertThat(headers1).containsEntry("Authorization", "Bearer cached-token");
        assertThat(headers2).isEqualTo(headers1);
        assertThat(headers3).isEqualTo(headers1);

        // verify token was fetched only once
        verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
      }
    }

    @Test
    void shouldRefreshTokenAfterExpiry() {
      // given - first token expires in 10 seconds
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .inScenario("Token Refresh")
              .whenScenarioStateIs("Started")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                      {
                        "access_token": "initial-token",
                        "token_type": "Bearer",
                        "expires_in": 10
                      }
                      """))
              .willSetStateTo("Token Issued"));

      // second token expires in 1 hour
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .inScenario("Token Refresh")
              .whenScenarioStateIs("Token Issued")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                      {
                        "access_token": "refreshed-token",
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      // simulate time passing with a controllable clock
      final var clock = new ControllableClock(BASE_INSTANT);
      final var supplier =
          new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, clock, auth);

      // token is cached before expiry
      final var headers1 = supplier.get();
      clock.advanceBy(Duration.ofSeconds(5));
      final var headers2 = supplier.get();

      assertThat(headers2)
          .isEqualTo(headers1)
          .containsEntry("Authorization", "Bearer initial-token");

      verify(1, postRequestedFor(urlEqualTo("/oauth/token")));

      // advance clock past the 10-second expiry
      clock.advanceBy(Duration.ofSeconds(11));

      final var headers3 = supplier.get();

      // then
      assertThat(headers3)
          .isNotEqualTo(headers1)
          .containsEntry("Authorization", "Bearer refreshed-token");

      // verify token was fetched twice
      verify(2, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    void shouldUseDefaultExpiryWhenServerDoesNotProvideIt() {
      // given - token response does not contain an expires_in field
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .inScenario("Token Refresh")
              .whenScenarioStateIs("Started")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                        {
                          "access_token": "initial-token",
                          "token_type": "Bearer"
                        }
                        """))
              .willSetStateTo("Token Issued"));

      // second token
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .inScenario("Token Refresh")
              .whenScenarioStateIs("Token Issued")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                        {
                          "access_token": "refreshed-token",
                          "token_type": "Bearer"
                        }
                        """)));

      final var auth =
          new OAuthAuthentication(
              tokenEndpoint,
              "my-client-id",
              "my-client-secret",
              null,
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      // simulate time passing with a controllable clock
      final var clock = new ControllableClock(BASE_INSTANT);
      final var supplier =
          new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, clock, auth);

      // token is cached before expiry
      final var headers1 = supplier.get();
      clock.advanceBy(Duration.ofMinutes(4)).advanceBy(Duration.ofSeconds(50));
      final var headers2 = supplier.get();

      assertThat(headers2)
          .isEqualTo(headers1)
          .containsEntry("Authorization", "Bearer initial-token");

      verify(1, postRequestedFor(urlEqualTo("/oauth/token")));

      // advance clock past the default 5 minute expiry
      clock.advanceBy(Duration.ofSeconds(11));

      final var headers3 = supplier.get();

      // then
      assertThat(headers3)
          .isNotEqualTo(headers1)
          .containsEntry("Authorization", "Bearer refreshed-token");

      // verify token was fetched twice
      verify(2, postRequestedFor(urlEqualTo("/oauth/token")));
    }
  }

  @Nested
  class ErrorHandling {

    @Test
    void shouldThrowErrorOnHttpFailure() {
      // given
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when/then
      assertThatThrownBy(supplier::get)
          .isInstanceOf(ConnectorException.class)
          .hasMessage(
              "MCP client authentication failed: Unauthorized - {error=invalid_client, error_description=Invalid client credentials}");
    }

    @Test
    void shouldThrowErrorWhenAccessTokenMissing() {
      // given
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                      {
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
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when/then
      assertThatThrownBy(supplier::get)
          .isInstanceOf(ConnectorException.class)
          .hasMessage(
              "MCP client authentication failed: Invalid OAuth token response. Missing 'access_token' field.");
    }

    @Test
    void shouldThrowErrorWhenResponseIsNotAJsonObject() {
      // given
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[1, 2, 3]")));

      final var auth =
          new OAuthAuthentication(
              tokenEndpoint,
              "my-client-id",
              "my-client-secret",
              null,
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when/then
      assertThatThrownBy(supplier::get)
          .isInstanceOf(ConnectorException.class)
          .hasMessage(
              "MCP client authentication failed: Invalid OAuth token response. Expected a JSON object, but received ARRAY");
    }

    @Test
    void shouldThrowErrorWhenResponseIsNotJson() {
      // given
      stubFor(
          post(urlEqualTo("/oauth/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "text/plain")
                      .withBody("Not a JSON response")));

      final var auth =
          new OAuthAuthentication(
              tokenEndpoint,
              "my-client-id",
              "my-client-secret",
              null,
              ClientAuthenticationMethod.BASIC_AUTH_HEADER,
              null);

      final var supplier = new OAuthHeadersSupplier(oAuthService, httpClient, objectMapper, auth);

      // when/then
      assertThatThrownBy(supplier::get)
          .isInstanceOf(ConnectorException.class)
          .hasMessage(
              "MCP client authentication failed: Response body is not valid JSON: Not a JSON response");
    }
  }

  private static class ControllableClock extends Clock {
    private Instant currentInstant;
    private final ZoneId zone = ZoneId.of("UTC");

    ControllableClock(Instant initialInstant) {
      this.currentInstant = initialInstant;
    }

    ControllableClock advanceBy(Duration duration) {
      this.currentInstant = currentInstant.plus(duration);
      return this;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }
  }
}
