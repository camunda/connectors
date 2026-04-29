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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * Local OIDC issuer for tests that need provider discovery/JWKS endpoints without depending on an
 * external identity provider.
 */
public final class MockOidcServer implements AutoCloseable {

  private static final String OPEN_ID_CONFIGURATION_PATH = "/.well-known/openid-configuration";
  private static final String JWKS_PATH = "/oauth2/jwks";
  private static final String TOKEN_PATH = "/token";
  private static final int DEFAULT_STUB_PRIORITY = 10;
  private static final int CUSTOM_STUB_PRIORITY = 1;

  private final WireMockServer server;

  private MockOidcServer(WireMockServer server) {
    this.server = server;
  }

  public static MockOidcServer start() {
    var server = new WireMockServer(options().dynamicPort());
    server.start();
    var mockOidcServer = new MockOidcServer(server);
    mockOidcServer.stubOidcEndpoints();
    return mockOidcServer;
  }

  public String issuer() {
    return server.baseUrl();
  }

  public String tokenUrl() {
    return server.baseUrl() + TOKEN_PATH;
  }

  public MockOidcServer stubOpenIdConfigurationResponse(String body) {
    return stubOpenIdConfigurationResponse(200, body);
  }

  public MockOidcServer stubOpenIdConfigurationResponse(int status, String body) {
    server.stubFor(
        WireMock.get(urlEqualTo(OPEN_ID_CONFIGURATION_PATH))
            .atPriority(CUSTOM_STUB_PRIORITY)
            .willReturn(jsonResponse(status, body)));
    return this;
  }

  public MockOidcServer stubJwksResponse(String body) {
    return stubJwksResponse(200, body);
  }

  public MockOidcServer stubJwksResponse(int status, String body) {
    server.stubFor(
        WireMock.get(urlEqualTo(JWKS_PATH))
            .atPriority(CUSTOM_STUB_PRIORITY)
            .willReturn(jsonResponse(status, body)));
    return this;
  }

  public MockOidcServer stubTokenResponse(String body) {
    return stubTokenResponse(200, body);
  }

  public MockOidcServer stubTokenResponse(int status, String body) {
    server.stubFor(
        WireMock.post(urlEqualTo(TOKEN_PATH))
            .atPriority(CUSTOM_STUB_PRIORITY)
            .willReturn(jsonResponse(status, body)));
    return this;
  }

  @Override
  public void close() {
    server.stop();
  }

  private void stubOidcEndpoints() {
    server.stubFor(
        WireMock.get(urlEqualTo(OPEN_ID_CONFIGURATION_PATH))
            .atPriority(DEFAULT_STUB_PRIORITY)
            .willReturn(
                jsonResponse(
                    200,
                    """
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s/oauth2/jwks",
                      "id_token_signing_alg_values_supported": ["RS256"]
                    }
                    """
                        .formatted(issuer(), issuer()))));
    server.stubFor(
        WireMock.get(urlEqualTo(JWKS_PATH))
            .atPriority(DEFAULT_STUB_PRIORITY)
            .willReturn(
                jsonResponse(
                    200,
                    """
                    {"keys": [%s]}
                    """
                        .formatted(jwk()))));
    server.stubFor(
        WireMock.post(urlEqualTo(TOKEN_PATH))
            .atPriority(DEFAULT_STUB_PRIORITY)
            .willReturn(jsonResponse(401, "")));
  }

  private static ResponseDefinitionBuilder jsonResponse(int status, String body) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(body);
  }

  private static String jwk() {
    try {
      var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      var keyPair = keyPairGenerator.generateKeyPair();
      var publicKey = (RSAPublicKey) keyPair.getPublic();
      return """
          {
            "kty": "RSA",
            "kid": "test-key",
            "use": "sig",
            "alg": "RS256",
            "n": "%s",
            "e": "%s"
          }
          """
          .formatted(
              base64Url(publicKey.getModulus().toByteArray()),
              base64Url(publicKey.getPublicExponent().toByteArray()));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create test JWK", e);
    }
  }

  private static String base64Url(byte[] bytes) {
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
