/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for XOAuth authentication that mock the OAuth server behavior. Simulates
 * Outlook/Google OAuth flow for email.
 */
class XOAuthAuthenticationIntegrationTest {

  private static WireMockServer wireMockServer;
  private JakartaUtils jakartaUtils;
  private String tokenEndpoint;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void setUp() {
    wireMockServer.resetAll();
    jakartaUtils = new JakartaUtils();
    tokenEndpoint = "http://localhost:" + wireMockServer.port() + "/oauth/token";
  }

  @Test
  void shouldSuccessfullyRefreshAccessToken_OutlookStyleResponse() {
    // Given - Mock Outlook OAuth token endpoint
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("refresh_token=my_refresh_token"))
            .withRequestBody(containing("client_id=my_client_id"))
            .withRequestBody(containing("client_secret=my_client_secret"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "token_type": "Bearer",
                          "scope": "https://outlook.office365.com/IMAP.AccessAsUser.All https://outlook.office365.com/POP.AccessAsUser.All https://outlook.office365.com/SMTP.Send",
                          "expires_in": 3666,
                          "ext_expires_in": 3666,
                          "access_token": "my-new-access-token",
                          "refresh_token": "my-new-refresh-token"
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@outlook.com",
            "my_refresh_token",
            "my_client_id",
            "my_client_secret",
            tokenEndpoint,
            "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send");

    // When
    String accessToken = jakartaUtils.refreshAccessToken(auth);

    // Then
    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("refresh_token=my_refresh_token"))
            .withRequestBody(containing("client_id=my_client_id"))
            .withRequestBody(containing("client_secret=my_client_secret")));

    assertEquals("my-new-access-token", accessToken);
  }

  @Test
  void shouldSuccessfullyRefreshAccessToken_GoogleStyleResponse() {
    // Given - Mock Google OAuth token endpoint
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("scope=https%3A%2F%2Fmail.google.com%2F"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "ya29.a0AfH6SMBxGoogleAccessToken",
                          "expires_in": 3599,
                          "scope": "https://mail.google.com/",
                          "token_type": "Bearer"
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@gmail.com",
            "google_refresh_token",
            "google_client_id",
            "google_client_secret",
            tokenEndpoint,
            "https://mail.google.com/");

    // When
    String accessToken = jakartaUtils.refreshAccessToken(auth);

    // Then
    assertEquals("ya29.a0AfH6SMBxGoogleAccessToken", accessToken);
    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withRequestBody(containing("scope=https%3A%2F%2Fmail.google.com%2F")));
  }

  @Test
  void shouldThrowException_WhenCredentialsAreInvalid_401Unauthorized() {
    // Given - OAuth server returns 401 for invalid client credentials
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": "invalid_client",
                          "error_description": "Client authentication failed. Invalid client_id or client_secret."
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token",
            "wrong_client_id",
            "wrong_client_secret",
            tokenEndpoint,
            null);

    // When/Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> jakartaUtils.refreshAccessToken(auth));

    assertTrue(
        exception.getMessage().contains("401")
            || exception.getMessage().contains("Unauthorized")
            || exception.getMessage().contains("Client authentication failed"));
  }

  @Test
  void shouldThrowException_WhenRefreshTokenIsInvalid_400BadRequest() {
    // Given - OAuth server returns 400 for invalid/expired refresh token
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": "invalid_grant",
                          "error_description": "The provided refresh token is invalid, expired, revoked, or was issued to another client."
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com",
            "expired_or_invalid_refresh_token",
            "client_id",
            "client_secret",
            tokenEndpoint,
            null);

    // When/Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> jakartaUtils.refreshAccessToken(auth));

    assertTrue(
        exception.getMessage().contains("400")
            || exception.getMessage().contains("Bad Request")
            || exception.getMessage().contains("invalid_grant"));
  }

  @Test
  void shouldThrowException_WhenAccessTokenIsMissingInResponse() {
    // Given - OAuth server returns success but without access_token field
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "expires_in": 3600,
                          "token_type": "Bearer",
                          "scope": "email"
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com", "refresh_token", "client_id", "client_secret", tokenEndpoint, null);

    // When/Then
    ConnectorException exception =
        assertThrows(ConnectorException.class, () -> jakartaUtils.refreshAccessToken(auth));

    assertTrue(
        exception.getMessage().contains("OAuth token endpoint did not return 'access_token'"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void shouldThrowException_WhenAccessTokenIsEmpty(String emtpyToken) {
    // Given - OAuth server returns empty access_token
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": %s,
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """
                            .formatted(emtpyToken))));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com", "refresh_token", "client_id", "client_secret", tokenEndpoint, null);

    // When/Then
    ConnectorException exception =
        assertThrows(ConnectorException.class, () -> jakartaUtils.refreshAccessToken(auth));

    assertTrue(
        exception.getMessage().contains("OAuth token endpoint did not return 'access_token'"));
  }

  @Test
  void shouldThrowException_WhenOAuthServerIsUnreachable() {
    // Given - Invalid endpoint URL
    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token",
            "client_id",
            "client_secret",
            "http://localhost:" + wireMockServer.port() + "/nonexistent-endpoint",
            null);

    // When/Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> jakartaUtils.refreshAccessToken(auth));

    // Should fail because endpoint doesn't exist
    assertNotNull(exception);
  }

  @Test
  void shouldHandleSpecialCharactersInCredentials() {
    // Given - Credentials with special characters that need URL encoding
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .withRequestBody(containing("refresh_token=token%2Bwith%2Fspecial%3Dchars%26symbols"))
            .withRequestBody(containing("client_secret=secret%21%40%23%24%25"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "special_chars_access_token"
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com",
            "token+with/special=chars&symbols",
            "client_id",
            "secret!@#$%",
            tokenEndpoint,
            null);

    // When
    String accessToken = jakartaUtils.refreshAccessToken(auth);

    // Then
    assertEquals("special_chars_access_token", accessToken);
    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withRequestBody(containing("refresh_token=token%2Bwith%2Fspecial%3Dchars%26symbols"))
            .withRequestBody(containing("client_secret=secret%21%40%23%24%25")));
  }

  @Test
  void shouldSendAllRequiredParametersInRequestBody() {
    // Given
    wireMockServer.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "test_token"
                        }
                        """)));

    XOAuthAuthentication auth =
        new XOAuthAuthentication(
            "user@example.com", "my_refresh", "my_client", "my_secret", tokenEndpoint, null);

    // When
    jakartaUtils.refreshAccessToken(auth);

    // Then - Verify all required OAuth parameters are present
    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("refresh_token=my_refresh"))
            .withRequestBody(containing("client_id=my_client"))
            .withRequestBody(containing("client_secret=my_secret")));
  }
}
