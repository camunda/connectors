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
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WebhookAuthChecker {

  private final WebhookAuthorization authorization;
  private final JwkProvider jwkProvider;
  private ObjectMapper objectMapper;

  public WebhookAuthChecker(WebhookAuthorization authorization) {
    if (authorization == null) {
      throw new IllegalArgumentException("Authorization must not be null");
    }

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

    if (authorization instanceof WebhookAuthorization.None) {
      // no auth expected, proceed
      return;
    }

    String authHeader = payload.headers().get(HttpHeaders.AUTHORIZATION);
    if (authHeader == null) {
      throw new IOException(AUTH_HEADER_MISSING_MSG);
    }
    String[] authHeaderParts = authHeader.split(" ");
    if (authHeaderParts.length != 2) {
      throwInvalid();
    }
    String authType = authHeaderParts[0];
    String authValue = authHeaderParts[1];

    if (authorization instanceof BasicAuth basicAuth) {
      checkBasicAuth(basicAuth, authType, authValue);
    } else if (authorization instanceof ApiKeyAuth apiKeyAuth) {
      checkApiKeyAuth(apiKeyAuth, authType, authValue);
    } else if (authorization instanceof JwtAuth jwtAuth) {
      checkJwtAuth(jwtAuth, payload.headers());
    } else {
      throw new IllegalStateException("Unsupported auth type");
    }
  }

  private void checkBasicAuth(BasicAuth expectedAuthorization, String authType, String authValue)
      throws IOException {

    if (!"basic".equalsIgnoreCase(authType)) {
      throwInvalid();
    }
    String expectedAuth = expectedAuthorization.username() + ":" + expectedAuthorization.password();
    String actualAuth = new String(Base64.getDecoder().decode(authValue));
    if (!expectedAuth.equals(actualAuth)) {
      throwInvalid();
    }
  }

  private void checkApiKeyAuth(ApiKeyAuth expectedAuthorization, String authType, String authValue)
      throws IOException {

    if (!"bearer".equalsIgnoreCase(authType)) {
      throwInvalid();
    }
    if (!expectedAuthorization.apiKey().equals(authValue)) {
      throwInvalid();
    }
  }

  private void checkJwtAuth(JwtAuth expectedAuthorization, Map<String, String> headers)
      throws IOException {
    if (!JWTChecker.verify(expectedAuthorization.jwt(), headers, this.jwkProvider, objectMapper)) {
      throw new IOException("Webhook failed: JWT check didn't pass");
    }
  }

  private static void throwInvalid() throws IOException {
    throw new IOException(AUTH_HEADER_INVALID_MSG);
  }

  private static final String AUTH_HEADER_INVALID_MSG = "Authorization header is invalid";
  private static final String AUTH_HEADER_MISSING_MSG = "Authorization header is missing";
}
