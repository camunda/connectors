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
package io.camunda.connector.runtime.auth;

import io.jsonwebtoken.Jwts;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * JWT Client Assertion authentication provider for Camunda Operate client. Supports P12 certificate
 * keystores for self-managed environments.
 */
public class OperateJwtClientAssertionAuth {

  private static final Logger LOG = LoggerFactory.getLogger(OperateJwtClientAssertionAuth.class);

  private final String clientId;
  private final String tokenEndpoint;
  private final String issuer;
  private final String audience;
  private final PrivateKey privateKey;
  private final String keyId;
  private final RestTemplate restTemplate;
  private final ReadWriteLock tokenLock = new ReentrantReadWriteLock();

  private String cachedToken;
  private Instant tokenExpiry;

  public OperateJwtClientAssertionAuth(
      String clientId,
      String certPath,
      String certPassword,
      String tokenEndpoint,
      String issuer,
      String audience) {
    if (clientId == null) {
      throw new IllegalArgumentException("Client ID cannot be null");
    }
    if (certPath == null) {
      throw new IllegalArgumentException("Certificate path cannot be null");
    }
    if (tokenEndpoint == null) {
      throw new IllegalArgumentException("Token endpoint cannot be null");
    }

    this.clientId = clientId;
    this.tokenEndpoint = tokenEndpoint;
    this.issuer = issuer != null ? issuer : clientId;
    this.audience = audience;
    this.restTemplate = new RestTemplate();

    try {
      if (certPath.toLowerCase().endsWith(".p12") || certPath.toLowerCase().endsWith(".pfx")) {
        var keyData = loadP12Certificate(certPath, certPassword);
        this.privateKey = keyData.getKey();
        this.keyId = keyData.getValue();
      } else {
        throw new IllegalArgumentException("Only P12/PFX certificate formats are supported");
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to load certificate for JWT client assertion", e);
    }
  }

  /** Load private key and x5t thumbprint from P12/PFX keystore */
  private Map.Entry<PrivateKey, String> loadP12Certificate(String certPath, String password)
      throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");

    try (FileInputStream fis = new FileInputStream(certPath)) {
      keyStore.load(fis, password != null ? password.toCharArray() : null);
    }

    String alias = keyStore.aliases().nextElement();
    PrivateKey privateKey =
        (PrivateKey) keyStore.getKey(alias, password != null ? password.toCharArray() : null);
    X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

    // Generate x5t thumbprint like Zeebe client
    String x5tThumbprint = generateX5tThumbprint(cert);

    return Map.entry(privateKey, x5tThumbprint);
  }

  /** Generate x5t thumbprint for certificate */
  private String generateX5tThumbprint(X509Certificate certificate) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] encoded = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate x5t thumbprint", e);
    }
  }

  /** Get an access token, using cached token if still valid */
  public String getAccessToken() {
    tokenLock.readLock().lock();
    try {
      if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
        LOG.debug("Using cached access token");
        return cachedToken;
      }
    } finally {
      tokenLock.readLock().unlock();
    }

    tokenLock.writeLock().lock();
    try {
      // Double-check after acquiring write lock
      if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
        LOG.debug("Using cached access token (double-check)");
        return cachedToken;
      }

      LOG.debug("Fetching new access token using JWT client assertion");
      String newToken = fetchAccessToken();

      // Cache token with 5-minute buffer before expiry
      cachedToken = newToken;
      tokenExpiry = Instant.now().plus(55, ChronoUnit.MINUTES);

      return newToken;
    } finally {
      tokenLock.writeLock().unlock();
    }
  }

  /** Fetch a new access token using JWT client assertion */
  private String fetchAccessToken() {
    String clientAssertion = createClientAssertion();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "client_credentials");
    body.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    body.add("client_assertion", clientAssertion);
    body.add("scope", clientId + "/.default");
    if (audience != null) {
      body.add("audience", audience);
    }

    // Log request details for debugging
    LOG.info("Sending OAuth2 JWT client assertion request to: {}", tokenEndpoint);
    LOG.debug("Request headers: {}", headers);
    LOG.debug(
        "Request body parameters: grant_type={}, client_assertion_type={}, scope={}, audience={}",
        body.getFirst("grant_type"),
        body.getFirst("client_assertion_type"),
        body.getFirst("scope"),
        body.getFirst("audience"));
    LOG.debug("Client assertion JWT: {}", clientAssertion);

    // Verify body is not empty
    if (body.isEmpty()) {
      LOG.error("Request body is empty! This should not happen.");
      throw new RuntimeException("OAuth request body is empty");
    }

    LOG.debug("Request body size: {} parameters", body.size());
    for (String key : body.keySet()) {
      LOG.debug("Body parameter '{}' has {} values", key, body.get(key).size());
    }

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);

      LOG.info("OAuth2 response status: {}", response.getStatusCode());
      LOG.debug("OAuth2 response headers: {}", response.getHeaders());
      LOG.debug("OAuth2 response body: {}", response.getBody());

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        String accessToken = (String) response.getBody().get("access_token");
        if (accessToken != null) {
          LOG.info("Successfully obtained access token via JWT client assertion");
          return accessToken;
        }
      }

      throw new RuntimeException(
          "Failed to get access token: invalid response from "
              + tokenEndpoint
              + ", response: "
              + response.getBody());
    } catch (Exception e) {
      LOG.error(
          "Failed to fetch access token using JWT client assertion from endpoint: {}",
          tokenEndpoint,
          e);

      // Try to extract more details from the error
      if (e instanceof org.springframework.web.client.HttpClientErrorException) {
        org.springframework.web.client.HttpClientErrorException httpError =
            (org.springframework.web.client.HttpClientErrorException) e;
        LOG.error("HTTP Error Response Body: {}", httpError.getResponseBodyAsString());
        LOG.error("HTTP Error Status Code: {}", httpError.getStatusCode());
        LOG.error("HTTP Error Headers: {}", httpError.getResponseHeaders());
      }

      throw new RuntimeException("OAuth2 token request failed: " + e.getMessage(), e);
    }
  }

  /** Create JWT client assertion for OAuth2 authentication */
  private String createClientAssertion() {
    Instant now = Instant.now();

    return Jwts.builder()
        .issuer(clientId)
        .subject(clientId)
        .audience()
        .add(issuer)
        .and()
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
        .id(UUID.randomUUID().toString())
        .header()
        .add("alg", "RS256")
        .add("typ", "JWT")
        .add("x5t", keyId)
        .and()
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }
}
