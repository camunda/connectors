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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/** Local OIDC issuer for tests; {@link #token()} mints tokens valid against its JWKS. */
public final class MockOidcServer implements AutoCloseable {

  private static final String OPEN_ID_CONFIGURATION_PATH = "/.well-known/openid-configuration";
  private static final String JWKS_PATH = "/oauth2/jwks";
  private static final String TOKEN_PATH = "/token";
  private static final String KEY_ID = "test-key";
  private static final int DEFAULT_STUB_PRIORITY = 10;
  private static final int CUSTOM_STUB_PRIORITY = 1;

  private final WireMockServer server;
  private final KeyPair signingKeyPair;

  private MockOidcServer(WireMockServer server, KeyPair signingKeyPair) {
    this.server = server;
    this.signingKeyPair = signingKeyPair;
  }

  public static MockOidcServer start() {
    var server = new WireMockServer(options().dynamicPort());
    server.start();
    var mockOidcServer = new MockOidcServer(server, generateSigningKeyPair());
    mockOidcServer.stubOidcEndpoints();
    return mockOidcServer;
  }

  public String issuer() {
    return server.baseUrl();
  }

  public String tokenUrl() {
    return server.baseUrl() + TOKEN_PATH;
  }

  /** A JWT signed with this server's key; defaults to one this issuer would accept. */
  public TokenBuilder token() {
    return new TokenBuilder(this);
  }

  public static final class TokenBuilder {

    private final MockOidcServer server;
    private final List<String> audience = new ArrayList<>();
    private String issuer;
    private String subject = "test-subject";
    private Instant issuedAt = Instant.now();
    private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));

    private TokenBuilder(MockOidcServer server) {
      this.server = server;
      this.issuer = server.issuer();
    }

    public TokenBuilder issuer(String issuer) {
      this.issuer = issuer;
      return this;
    }

    public TokenBuilder subject(String subject) {
      this.subject = subject;
      return this;
    }

    public TokenBuilder audience(String... audience) {
      this.audience.addAll(Arrays.asList(audience));
      return this;
    }

    public TokenBuilder issuedAt(Instant issuedAt) {
      this.issuedAt = issuedAt;
      return this;
    }

    public TokenBuilder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public String sign() {
      var header =
          """
          {"alg":"RS256","typ":"JWT","kid":"%s"}"""
              .formatted(KEY_ID);
      var claims = new ArrayList<String>();
      claims.add("\"iss\":\"%s\"".formatted(issuer));
      claims.add("\"sub\":\"%s\"".formatted(subject));
      claims.add("\"iat\":%d".formatted(issuedAt.getEpochSecond()));
      claims.add("\"exp\":%d".formatted(expiresAt.getEpochSecond()));
      if (!audience.isEmpty()) {
        claims.add(
            "\"aud\":[%s]"
                .formatted(
                    audience.stream().map("\"%s\""::formatted).collect(Collectors.joining(","))));
      }
      var payload = "{" + String.join(",", claims) + "}";

      var signingInput =
          base64Url(header.getBytes(StandardCharsets.UTF_8))
              + "."
              + base64Url(payload.getBytes(StandardCharsets.UTF_8));
      return signingInput + "." + base64Url(server.sign(signingInput));
    }
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
                      "jwks_uri": "%s%s",
                      "id_token_signing_alg_values_supported": ["RS256"]
                    }
                    """
                        .formatted(issuer(), issuer(), JWKS_PATH))));
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

  private static KeyPair generateSigningKeyPair() {
    try {
      var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create test signing key", e);
    }
  }

  private byte[] sign(String signingInput) {
    try {
      var signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(signingKeyPair.getPrivate());
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign test token", e);
    }
  }

  private String jwk() {
    var publicKey = (RSAPublicKey) signingKeyPair.getPublic();
    return """
        {
          "kty": "RSA",
          "kid": "%s",
          "use": "sig",
          "alg": "RS256",
          "n": "%s",
          "e": "%s"
        }
        """
        .formatted(
            KEY_ID,
            base64UrlMagnitude(publicKey.getModulus()),
            base64UrlMagnitude(publicKey.getPublicExponent()));
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Strips BigInteger's sign byte for JWK n/e. Never use on arbitrary bytes like a signature. */
  private static String base64UrlMagnitude(BigInteger value) {
    var bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return base64Url(bytes);
  }
}
