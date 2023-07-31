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
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import java.net.URI;
import java.util.concurrent.TimeUnit;

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
   */
  public AuthorizationHandler<?> getHandler(WebhookProcessingPayload payload) {

    if (authorization == null || authorization instanceof WebhookAuthorization.None) {
      // no auth expected, proceed
      return AuthorizationHandler.noOp();
    }

    if (authorization instanceof BasicAuth basicAuth) {
      return new BasicAuthHandler(basicAuth, payload);
    } else if (authorization instanceof ApiKeyAuth apiKeyAuth) {
      return new ApiKeyAuthHandler(apiKeyAuth, payload);
    } else if (authorization instanceof JwtAuth jwtAuth) {
      return new JWTAuthHandler(jwtAuth, payload, jwkProvider, objectMapper);
    } else {
      throw new IllegalStateException("Unsupported auth type");
    }
  }
}
