/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.authorization.AuthorizationHandler.NoOpAuthorizationHandler;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.None;

public abstract sealed class AuthorizationHandler<T extends WebhookAuthorization>
    permits ApiKeyAuthHandler, NoOpAuthorizationHandler, BasicAuthHandler, JWTAuthHandler {
  protected T expectedAuthorization;
  protected WebhookProcessingPayload payload;

  public AuthorizationHandler(T authorization, WebhookProcessingPayload payload) {
    this.expectedAuthorization = authorization;
    this.payload = payload;
  }

  /**
   * Check if authorization is present in the payload. If false, the webhook endpoint returns 401.
   */
  public abstract boolean isPresent();

  /** Check if authorization is valid. If false, the webhook endpoint returns 403. */
  public abstract boolean isValid();

  public static NoOpAuthorizationHandler noOp() {
    return NoOpAuthorizationHandler.INSTANCE;
  }

  static final class NoOpAuthorizationHandler extends AuthorizationHandler<None> {

    private static final NoOpAuthorizationHandler INSTANCE = new NoOpAuthorizationHandler();

    private NoOpAuthorizationHandler() {
      super(null, null);
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isValid() {
      return true;
    }
  }
}
