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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.http.client.model.auth.OAuthRefreshTokenAuthentication;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OAuthRefreshTokenServiceTest {

  private final OAuthService oAuthService = new OAuthService();
  private final ObjectMapper objectMapper = HttpClientObjectMapperSupplier.getCopy();

  @Nested
  class CreateOAuthRefreshTokenRequestTests {

    @Test
    void shouldCreateRequestWithRefreshTokenGrantType() {
      var auth =
          new OAuthRefreshTokenAuthentication(
              "https://login.microsoftonline.com/tenant/oauth2/v2.0/token",
              "clientId",
              "clientSecret",
              "theRefreshToken",
              "https://graph.microsoft.com/.default");

      var request = oAuthService.createOAuthRefreshTokenRequestFrom(auth);

      assertThat(request.getUrl())
          .isEqualTo("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
      assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
      assertThat(request.getHeaders().orElse(Map.of()))
          .containsEntry("Content-Type", "application/x-www-form-urlencoded");
      assertThat((Map) request.getBody()).containsEntry("grant_type", "refresh_token");
      assertThat((Map) request.getBody()).containsEntry("client_id", "clientId");
      assertThat((Map) request.getBody()).containsEntry("client_secret", "clientSecret");
      assertThat((Map) request.getBody()).containsEntry("refresh_token", "theRefreshToken");
      assertThat((Map) request.getBody())
          .containsEntry("scope", "https://graph.microsoft.com/.default");
    }

    @Test
    void shouldOmitClientSecretWhenBlank() {
      var auth =
          new OAuthRefreshTokenAuthentication(
              "https://login.microsoftonline.com/tenant/oauth2/v2.0/token",
              "clientId",
              null,
              "theRefreshToken",
              null);

      var request = oAuthService.createOAuthRefreshTokenRequestFrom(auth);

      assertThat((Map) request.getBody()).doesNotContainKey("client_secret");
      assertThat((Map) request.getBody()).doesNotContainKey("scope");
      assertThat((Map) request.getBody()).containsEntry("grant_type", "refresh_token");
      assertThat((Map) request.getBody()).containsEntry("client_id", "clientId");
      assertThat((Map) request.getBody()).containsEntry("refresh_token", "theRefreshToken");
    }
  }

  @Nested
  class ExtractTokenFromRefreshTokenResponseTests {

    @Test
    void shouldReturnToken_whenResponseContainsAccessToken() throws JsonProcessingException {
      var body =
          Map.of(
              "access_token", "myAccessToken",
              "token_type", "Bearer",
              "expires_in", 3600);
      String json = objectMapper.writeValueAsString(body);
      var response =
          new StreamingHttpResponse(200, null, null, new ByteArrayInputStream(json.getBytes()));

      var tokenResponse = oAuthService.extractTokenFromRefreshTokenResponse(response);

      assertThat(tokenResponse.accessToken()).isEqualTo("myAccessToken");
      assertThat(tokenResponse.expiresInSeconds()).hasValue(3600);
    }

    @Test
    void shouldThrowInvalidGrantException_whenResponseContainsInvalidGrant() {
      String body =
          "{\"error\":\"invalid_grant\",\"error_description\":\"AADSTS70008: Refresh token expired.\"}";
      var response =
          new StreamingHttpResponse(400, null, null, new ByteArrayInputStream(body.getBytes()));

      assertThatThrownBy(() -> oAuthService.extractTokenFromRefreshTokenResponse(response))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("re-authorization is required")
          .hasMessageContaining("AADSTS70008");
    }

    @Test
    void shouldThrowInteractionRequiredException_whenResponseContainsInteractionRequired() {
      String body =
          "{\"error\":\"interaction_required\",\"error_description\":\"User must re-authenticate.\"}";
      var response =
          new StreamingHttpResponse(400, null, null, new ByteArrayInputStream(body.getBytes()));

      assertThatThrownBy(() -> oAuthService.extractTokenFromRefreshTokenResponse(response))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("re-authorization is required")
          .hasMessageContaining("User must re-authenticate");
    }

    @Test
    void shouldThrowGenericOAuthError_whenResponseContainsUnknownError() {
      String body =
          "{\"error\":\"unauthorized_client\",\"error_description\":\"Client not allowed.\"}";
      var response =
          new StreamingHttpResponse(400, null, null, new ByteArrayInputStream(body.getBytes()));

      assertThatThrownBy(() -> oAuthService.extractTokenFromRefreshTokenResponse(response))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("unauthorized_client");
    }

    @Test
    void shouldThrowOAuthTokenError_whenResponseHasNoAccessToken() {
      String body = "{\"scope\":\"read\",\"token_type\":\"Bearer\"}";
      var response =
          new StreamingHttpResponse(200, null, null, new ByteArrayInputStream(body.getBytes()));

      assertThatThrownBy(() -> oAuthService.extractTokenFromRefreshTokenResponse(response))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("access_token");
    }
  }
}
