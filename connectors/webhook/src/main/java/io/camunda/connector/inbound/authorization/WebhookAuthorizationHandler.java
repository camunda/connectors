/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.auth0.jwk.JwkProviderBuilder;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationHandler.NoOpAuthorizationHandler;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.None;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public abstract sealed class WebhookAuthorizationHandler<T extends WebhookAuthorization>
    permits ApiKeyAuthHandler, NoOpAuthorizationHandler, BasicAuthHandler, JWTAuthHandler {
  protected T expectedAuthorization;

  public WebhookAuthorizationHandler(T authorization) {
    this.expectedAuthorization = authorization;
  }

  /**
   * Check if authorization is present in the payload. If false, the webhook endpoint returns 401.
   */
  public abstract AuthorizationResult checkAuthorization(WebhookProcessingPayload payload);

  public static WebhookAuthorizationHandler<?> getHandlerForAuth(
      WebhookAuthorization authorization) {
    if (authorization == null || authorization instanceof None) {
      return NoOpAuthorizationHandler.INSTANCE;
    } else if (authorization instanceof BasicAuth basicAuth) {
      return new BasicAuthHandler(basicAuth);
    } else if (authorization instanceof ApiKeyAuth apiKeyAuth) {
      return new ApiKeyAuthHandler(apiKeyAuth);
    } else if (authorization instanceof JwtAuth jwtAuth) {
      try {
        var jwkProvider =
            new JwkProviderBuilder(URI.create(jwtAuth.jwt().jwkUrl()).toURL())
                .cached(10, 10, TimeUnit.MINUTES) // Cache JWKs for 10 minutes
                .rateLimited(10, 1, TimeUnit.MINUTES) // Rate limit to 10 requests per minute
                .build();
        return new JWTAuthHandler(jwtAuth, jwkProvider, ConnectorsObjectMapperSupplier.getCopy());
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize JWK provider", e);
      }
    }
    throw new IllegalStateException("Unsupported auth type");
  }

  static final class NoOpAuthorizationHandler extends WebhookAuthorizationHandler<None> {

    private static final NoOpAuthorizationHandler INSTANCE = new NoOpAuthorizationHandler();

    private NoOpAuthorizationHandler() {
      super(null);
    }

    @Override
    public AuthorizationResult checkAuthorization(WebhookProcessingPayload payload) {
      return Success.INSTANCE;
    }
  }
}
