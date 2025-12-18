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
package io.camunda.connector.runtime.core.intrinsic.functions;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class CreateGithubAppInstallationTokenFunction implements IntrinsicFunctionProvider {

  // This is the default expiration time for the JWT, not the installation token.
  // GitHub documentation specifies a maximum of 1 minute.
  private static final long JWT_EXPIRATION_SECONDS = 60L;
  private static final String RSA_ALGORITHM = "RSA";
  private static final String DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com";
  private static final String INSTALLATION_TOKEN_URL_FORMAT = "/app/installations/%s/access_tokens";
  private static final String HEADER_ACCEPT = "Accept";
  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String GITHUB_API_VERSION_ACCEPT_HEADER = "application/vnd.github.v3+json";
  public static final String RESPONSE_TOKEN_FIELD = "token";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private final String baseUrl;

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  public CreateGithubAppInstallationTokenFunction() {
    this(DEFAULT_GITHUB_API_BASE_URL);
  }

  // Package-private constructor for testing
  CreateGithubAppInstallationTokenFunction(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @IntrinsicFunction(name = "createGithubAppInstallationToken")
  public String execute(String privateKey, String appId, String installationId) {
    try {
      final String jwt = createJwt(privateKey, appId);
      return getInstallationAccessToken(jwt, installationId);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate GitHub App installation token", e);
    }
  }

  private String createJwt(String privateKey, String appId)
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    final RSAPrivateKey rsaPrivateKey = parsePrivateKey(privateKey);
    final Instant now = Instant.now();
    final Algorithm algorithm = Algorithm.RSA256(rsaPrivateKey);

    return JWT.create()
        .withIssuer(appId)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(now.plusSeconds(JWT_EXPIRATION_SECONDS))
        .sign(algorithm);
  }

  private String getInstallationAccessToken(String jwt, String installationId)
      throws IOException, InterruptedException {
    final String url = baseUrl + String.format(INSTALLATION_TOKEN_URL_FORMAT, installationId);
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(HEADER_ACCEPT, GITHUB_API_VERSION_ACCEPT_HEADER)
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + jwt)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      final Map<String, Object> body =
          objectMapper.readValue(response.body(), new TypeReference<>() {});
      final String token = (String) body.get(RESPONSE_TOKEN_FIELD);
      if (token == null || token.isBlank()) {
        throw new RuntimeException("Response from GitHub API did not contain a token");
      }
      return token;
    } else {
      throw new RuntimeException(
          "Request to GitHub API failed with status code "
              + response.statusCode()
              + " and body: "
              + response.body());
    }
  }

  private RSAPrivateKey parsePrivateKey(String privateKey)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    String normalizedKey = normalizePrivateKey(privateKey);
    try (PemReader pemReader = new PemReader(new StringReader(normalizedKey))) {
      PemObject pemObject = pemReader.readPemObject();
      if (pemObject == null) {
        throw new IllegalArgumentException(
            "Failed to parse PEM, no content found. "
                + "Ensure the private key is properly formatted with BEGIN/END markers.");
      }
      byte[] content = pemObject.getContent();
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(content);
      KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
      return (RSAPrivateKey) kf.generatePrivate(spec);
    }
  }

  // Package-private for testing
  String normalizePrivateKey(String privateKey) {
    // Handle escaped newlines from environment variables
    String normalized = privateKey.replace("\\n", "\n");

    // Trim any leading/trailing whitespace
    normalized = normalized.trim();

    // If the key already has proper newlines, just ensure consistent formatting
    if (normalized.contains("\n")) {
      // Clean up any irregular spacing around headers
      normalized =
          normalized
              .replaceAll(
                  "\\s*-----BEGIN RSA PRIVATE KEY-----\\s*", "-----BEGIN RSA PRIVATE KEY-----\n")
              .replaceAll(
                  "\\s*-----END RSA PRIVATE KEY-----\\s*", "\n-----END RSA PRIVATE KEY-----");
      return normalized;
    }

    // If it's a single-line key, we need to add proper newlines
    String beginMarker = "-----BEGIN RSA PRIVATE KEY-----";
    String endMarker = "-----END RSA PRIVATE KEY-----";

    int beginIndex = normalized.indexOf(beginMarker);
    int endIndex = normalized.indexOf(endMarker);

    if (beginIndex == -1 || endIndex == -1 || endIndex <= beginIndex) {
      // Invalid format, return as-is and let the parser handle the error
      return normalized;
    }

    String content =
        normalized
            .substring(beginIndex + beginMarker.length(), endIndex)
            .replaceAll("\\s+", ""); // Remove all whitespace from the base64 content

    // Format with 64-character lines as per PEM standard
    StringBuilder formatted = new StringBuilder();
    formatted.append(beginMarker).append("\n");

    for (int i = 0; i < content.length(); i += 64) {
      formatted.append(content, i, Math.min(content.length(), i + 64)).append("\n");
    }

    formatted.append(endMarker);

    return formatted.toString();
  }
}
