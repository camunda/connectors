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
package io.camunda.connector.test.utils.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;

class MockOidcServerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldServeOpenIdConfiguration() throws Exception {
    try (var server = MockOidcServer.start()) {
      var response =
          httpClient.send(
              HttpRequest.newBuilder(
                      URI.create(server.issuer() + "/.well-known/openid-configuration"))
                  .GET()
                  .build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"issuer\": \"" + server.issuer() + "\"")
          .contains("\"jwks_uri\": \"" + server.issuer() + "/oauth2/jwks\"");
    }
  }

  @Test
  void shouldServeJwks() throws Exception {
    try (var server = MockOidcServer.start()) {
      var response =
          httpClient.send(
              HttpRequest.newBuilder(URI.create(server.issuer() + "/oauth2/jwks")).GET().build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"keys\"")
          .contains("\"kty\": \"RSA\"")
          .contains("\"alg\": \"RS256\"");
    }
  }

  @Test
  void shouldRejectTokenRequests() throws Exception {
    try (var server = MockOidcServer.start()) {
      var response =
          httpClient.send(
              HttpRequest.newBuilder(URI.create(server.tokenUrl()))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(401);
    }
  }

  @Test
  void shouldAllowCustomOpenIdConfigurationResponse() throws Exception {
    try (var server = MockOidcServer.start()) {
      server.stubOpenIdConfigurationResponse(503, "{\"error\":\"unavailable\"}");

      var response =
          httpClient.send(
              HttpRequest.newBuilder(
                      URI.create(server.issuer() + "/.well-known/openid-configuration"))
                  .GET()
                  .build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(response.body()).isEqualTo("{\"error\":\"unavailable\"}");
    }
  }

  @Test
  void shouldAllowCustomJwksResponse() throws Exception {
    try (var server = MockOidcServer.start()) {
      server.stubJwksResponse(500, "{\"keys\":[]}");

      var response =
          httpClient.send(
              HttpRequest.newBuilder(URI.create(server.issuer() + "/oauth2/jwks")).GET().build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(500);
      assertThat(response.body()).isEqualTo("{\"keys\":[]}");
    }
  }

  @Test
  void shouldAllowCustomTokenResponse() throws Exception {
    try (var server = MockOidcServer.start()) {
      server.stubTokenResponse(
          """
          {
            "access_token": "test-access-token",
            "token_type": "Bearer",
            "expires_in": 3600
          }
          """);

      var response =
          httpClient.send(
              HttpRequest.newBuilder(URI.create(server.tokenUrl()))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"access_token\": \"test-access-token\"");
    }
  }
}
