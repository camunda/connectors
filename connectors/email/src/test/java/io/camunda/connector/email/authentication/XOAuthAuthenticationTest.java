/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class XOAuthAuthenticationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldCreateXOAuthAuthenticationInstance() {
    // When
    XOAuthAuthentication xoauth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token_123",
            "client_id_456",
            "client_secret_789",
            "https://oauth.example.com/token",
            "https://mail.google.com/");

    // Then
    assertNotNull(xoauth);
    assertEquals("user@example.com", xoauth.username());
    assertEquals("refresh_token_123", xoauth.refreshToken());
    assertEquals("client_id_456", xoauth.clientId());
    assertEquals("client_secret_789", xoauth.clientSecret());
    assertEquals("https://oauth.example.com/token", xoauth.tokenEndpoint());
    assertEquals("https://mail.google.com/", xoauth.scope());
    assertEquals(XOAuthAuthentication.TYPE, "xoauth-user-credentials-flow");
  }

  @Test
  void shouldGenerateRequestAccessTokenBodyWithScope() {
    // Given
    XOAuthAuthentication xoauth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token_123",
            "client_id_456",
            "client_secret_789",
            "https://oauth.example.com/token",
            "https://mail.google.com/");

    // When
    String body = xoauth.getRequestAccessTokenBody();

    // Then
    assertTrue(body.contains("grant_type=refresh_token"));
    assertTrue(body.contains("refresh_token=refresh_token_123"));
    assertTrue(body.contains("client_id=client_id_456"));
    assertTrue(body.contains("client_secret=client_secret_789"));
    assertTrue(body.contains("scope=https%3A%2F%2Fmail.google.com%2F"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldGenerateRequestAccessTokenBodyWithoutScope(String emptyScope) {
    // Given
    XOAuthAuthentication xoauth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token_123",
            "client_id_456",
            "client_secret_789",
            "https://oauth.example.com/token",
            emptyScope);

    // When
    String body = xoauth.getRequestAccessTokenBody();

    // Then
    assertTrue(body.contains("grant_type=refresh_token"));
    assertTrue(body.contains("refresh_token=refresh_token_123"));
    assertTrue(body.contains("client_id=client_id_456"));
    assertTrue(body.contains("client_secret=client_secret_789"));
    assertFalse(body.contains("scope="));
  }

  @Test
  void shouldUrlEncodeSpecialCharactersInRequestBody() {
    // Given
    XOAuthAuthentication xoauth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh+token/with=special&chars",
            "client_id_456",
            "client_secret_789",
            "https://oauth.example.com/token",
            "scope with spaces");

    // When
    String body = xoauth.getRequestAccessTokenBody();

    // Then
    assertTrue(body.contains("refresh_token=refresh%2Btoken%2Fwith%3Dspecial%26chars"));
    assertTrue(body.contains("scope=scope+with+spaces"));
  }

  @Test
  void shouldSerializeToJson() throws Exception {
    // Given
    XOAuthAuthentication xoauth =
        new XOAuthAuthentication(
            "user@example.com",
            "refresh_token_123",
            "client_id_456",
            "client_secret_789",
            "https://oauth.example.com/token",
            "https://mail.google.com/");

    // When
    String json = objectMapper.writeValueAsString(xoauth);

    // Then
    assertTrue(json.contains("\"type\":\"xoauth-user-credentials-flow\""));
    assertTrue(json.contains("\"username\":\"user@example.com\""));
    assertTrue(json.contains("\"refreshToken\":\"refresh_token_123\""));
    assertTrue(json.contains("\"clientId\":\"client_id_456\""));
    assertTrue(json.contains("\"clientSecret\":\"client_secret_789\""));
    assertTrue(json.contains("\"tokenEndpoint\":\"https://oauth.example.com/token\""));
    assertTrue(json.contains("\"scope\":\"https://mail.google.com/\""));
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    // Given
    String json =
        """
        {
          "type": "xoauth-user-credentials-flow",
          "username": "user@example.com",
          "refreshToken": "refresh_token_123",
          "clientId": "client_id_456",
          "clientSecret": "client_secret_789",
          "tokenEndpoint": "https://oauth.example.com/token",
          "scope": "https://mail.google.com/"
        }
        """;

    // When
    Authentication auth = objectMapper.readValue(json, Authentication.class);

    // Then
    assertInstanceOf(XOAuthAuthentication.class, auth);
    XOAuthAuthentication xoauth = (XOAuthAuthentication) auth;
    assertEquals("user@example.com", xoauth.username());
    assertEquals("refresh_token_123", xoauth.refreshToken());
    assertEquals("client_id_456", xoauth.clientId());
    assertEquals("client_secret_789", xoauth.clientSecret());
    assertEquals("https://oauth.example.com/token", xoauth.tokenEndpoint());
    assertEquals("https://mail.google.com/", xoauth.scope());
  }

  @Test
  void shouldDeserializeFromJsonWithNullScope() throws Exception {
    // Given
    String json =
        """
        {
          "type": "xoauth-user-credentials-flow",
          "username": "user@example.com",
          "refreshToken": "refresh_token_123",
          "clientId": "client_id_456",
          "clientSecret": "client_secret_789",
          "tokenEndpoint": "https://oauth.example.com/token",
          "scope": null
        }
        """;

    // When
    Authentication auth = objectMapper.readValue(json, Authentication.class);

    // Then
    assertInstanceOf(XOAuthAuthentication.class, auth);
    XOAuthAuthentication xoauth = (XOAuthAuthentication) auth;
    assertNull(xoauth.scope());
  }

  @Test
  void shouldDeserializeFromJsonWithoutScopeField() throws Exception {
    // Given
    String json =
        """
        {
          "type": "xoauth-user-credentials-flow",
          "username": "user@example.com",
          "refreshToken": "refresh_token_123",
          "clientId": "client_id_456",
          "clientSecret": "client_secret_789",
          "tokenEndpoint": "https://oauth.example.com/token"
        }
        """;

    // When
    Authentication auth = objectMapper.readValue(json, Authentication.class);

    // Then
    assertInstanceOf(XOAuthAuthentication.class, auth);
    XOAuthAuthentication xoauth = (XOAuthAuthentication) auth;
    assertNull(xoauth.scope());
  }
}
