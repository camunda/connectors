/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OAuthServiceTest {
  private final OAuthService oAuthService = new OAuthService();

  @Nested
  class CreateOAuthRequestTests {
    @Test
    public void
        shouldCreateOAuthRequestWithBodyCredentials_whenCreatingRequestWithBodyCredentials() {
      // Given
      var auth =
          new OAuthAuthentication(
              "www.example.com",
              "clientId",
              "clientSecret",
              "theAudience",
              OAuthConstants.CREDENTIALS_BODY,
              "theScope");

      // When
      var request = oAuthService.createOAuthRequestFrom(auth);

      // Then
      assertThat(request.getUrl()).isEqualTo("www.example.com");
      assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
      assertThat(request.getHeaders())
          .containsEntry("Content-Type", "application/x-www-form-urlencoded");
      assertThat((Map) request.getBody()).containsEntry("client_id", "clientId");
      assertThat((Map) request.getBody()).containsEntry("client_secret", "clientSecret");
      assertThat((Map) request.getBody()).containsEntry("audience", "theAudience");
      assertThat((Map) request.getBody()).containsEntry("grant_type", "client_credentials");
      assertThat((Map) request.getBody()).containsEntry("scope", "theScope");
    }

    @Test
    public void shouldCreateOAuthRequestWithBasicHeader_whenCreatingRequestWithHeaderCredentials() {
      // Given
      var auth =
          new OAuthAuthentication(
              "www.example.com",
              "clientId",
              "clientSecret",
              "theAudience",
              OAuthConstants.BASIC_AUTH_HEADER,
              "theScope");

      // When
      var request = oAuthService.createOAuthRequestFrom(auth);

      // Then
      assertThat(request.getUrl()).isEqualTo("www.example.com");
      assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
      assertThat(request.getHeaders())
          .containsEntry("Content-Type", "application/x-www-form-urlencoded");
      assertThat(request.getHeaders())
          .containsEntry(
              "Authorization",
              Base64Helper.buildBasicAuthenticationHeader("clientId", "clientSecret"));
      assertThat((Map) request.getBody()).containsEntry("audience", "theAudience");
      assertThat((Map) request.getBody()).containsEntry("grant_type", "client_credentials");
      assertThat((Map) request.getBody()).containsEntry("scope", "theScope");
    }
  }

  @Nested
  class ExtractTokenFromResponseTests {
    @Test
    public void shouldReturnNull_whenExtractingTokenFromInvalidJson()
        throws JsonProcessingException {
      // Given
      String body = "invalidBody";

      // When
      String token = oAuthService.extractTokenFromResponse(body);

      // Then
      assertNull(token);
    }

    @Test
    public void shouldReturnNull_whenExtractingTokenFromJsonWithoutAccessToken()
        throws JsonProcessingException {
      // Given
      String body = "{\"scope\":\"read:clients\", \"expires_in\":86400,\"token_type\":\"Bearer\"}";

      // When
      String token = oAuthService.extractTokenFromResponse(body);

      // Then
      assertNull(token);
    }

    @Test
    public void shouldReturnToken_whenExtractingTokenFromValidJson()
        throws JsonProcessingException {
      // Given
      var body =
          Map.of(
              "access_token",
              "abcd",
              "scope",
              "read:clients",
              "expires_in",
              86400,
              "token_type",
              "Bearer");

      // When
      String token = oAuthService.extractTokenFromResponse(body);

      // Then
      assertThat(token).isEqualTo("abcd");
    }

    @Test
    public void shouldReturnToken_whenExtractingTokenFromValidJsonString()
        throws JsonProcessingException {
      // Given
      String body =
          "{\"access_token\": \"abcd\", \"scope\":\"read:clients\", \"expires_in\":86400,\"token_type\":\"Bearer\"}";

      // When
      String token = oAuthService.extractTokenFromResponse(body);

      // Then
      assertThat(token).isEqualTo("abcd");
    }
  }
}
