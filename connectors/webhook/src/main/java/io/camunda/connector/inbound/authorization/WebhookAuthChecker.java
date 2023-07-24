/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext.Request;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WebhookAuthChecker {

  private final WebhookAuthorization authorization;
  private final JwkProvider jwkProvider;
  private ObjectMapper objectMapper;

  public WebhookAuthChecker(WebhookAuthorization authorization) {

    this.authorization = authorization;

    if (authorization instanceof JwtAuth jwtAuth) {
      try {
        this.jwkProvider =
            new JwkProviderBuilder(URI.create(jwtAuth.jwt().jwkUrl()).toURL())
                .cached(10, 10, TimeUnit.MINUTES) // Cache JWKs for 10 minutes
                .rateLimited(10, 1, TimeUnit.MINUTES) // Rate limit to 10 requests per minute
                .build();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize JWK provider", e);
      }
    } else {
      this.jwkProvider = null;
    }
  }

  public WebhookAuthChecker(WebhookAuthorization authorization, ObjectMapper objectMapper) {
    this(authorization);
    this.objectMapper = objectMapper;
  }

  /**
   * Check auth header against expected auth. Doesn't return anything, but throws an exception if
   * auth is not valid.
   *
   * @throws IOException if auth is not valid
   */
  public void checkAuthorization(WebhookProcessingPayload payload) throws IOException {

    if (authorization == null || authorization instanceof WebhookAuthorization.None) {
      // no auth expected, proceed
      return;
    }

    if (authorization instanceof BasicAuth basicAuth) {
      checkBasicAuth(basicAuth, payload);
    } else if (authorization instanceof ApiKeyAuth apiKeyAuth) {
      checkApiKeyAuth(apiKeyAuth, payload);
    } else if (authorization instanceof JwtAuth jwtAuth) {
      checkJwtAuth(jwtAuth, payload);
    } else {
      throw new IllegalStateException("Unsupported auth type");
    }
  }

  private void checkBasicAuth(BasicAuth expectedAuthorization, WebhookProcessingPayload payload)
      throws IOException {

    String authHeader =
        payload.headers().entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Entry::getValue))
            .get(HttpHeaders.AUTHORIZATION.toLowerCase());

    if (authHeader == null) {
      throw new IOException(AUTH_HEADER_MISSING_MSG);
    }
    String[] authHeaderParts = authHeader.split(" ");
    if (authHeaderParts.length != 2) {
      throwInvalid();
    }
    String authType = authHeaderParts[0];
    String authValue = authHeaderParts[1];

    if (!"basic".equalsIgnoreCase(authType)) {
      throwInvalid();
    }
    String expectedAuth = expectedAuthorization.username() + ":" + expectedAuthorization.password();
    String actualAuth =
        new String(Base64.getDecoder().decode(authValue.getBytes(StandardCharsets.UTF_8)));
    if (!expectedAuth.equals(actualAuth)) {
      throwInvalid();
    }
  }

  private void checkApiKeyAuth(ApiKeyAuth expectedAuthorization, WebhookProcessingPayload payload)
      throws IOException {

    WebhookResultContext result =
        new WebhookResultContext(
            new Request(
                HttpWebhookUtil.transformRawBodyToMap(
                    payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())),
                payload.headers(),
                payload.params(),
                Map.of()));

    String authValue = expectedAuthorization.apiKeyLocator().apply(result);
    if (!expectedAuthorization.apiKey().equals(authValue)) {
      throwInvalid();
    }
  }

  private void checkJwtAuth(JwtAuth expectedAuthorization, WebhookProcessingPayload payload)
      throws IOException {
    if (!JWTChecker.verify(
        expectedAuthorization.jwt(), payload.headers(), this.jwkProvider, objectMapper)) {
      throw new IOException("Webhook failed: JWT check didn't pass");
    }
  }

  private static void throwInvalid() throws IOException {
    throw new IOException(AUTH_HEADER_INVALID_MSG);
  }

  private static final String AUTH_HEADER_INVALID_MSG = "Authorization header is invalid";
  private static final String AUTH_HEADER_MISSING_MSG = "Authorization header is missing";
}
