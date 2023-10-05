/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException.Reason;
import io.netty.handler.codec.http.HttpResponseStatus;

public abstract sealed class AuthorizationResult {

  public static final class Success extends AuthorizationResult {
    private Success() {}

    public static final Success INSTANCE = new Success();
  }

  public abstract static sealed class Failure extends AuthorizationResult {
    private final String message;

    public Failure(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    public abstract WebhookSecurityException toException();

    /** Authorization is missing or invalid */
    public static final class InvalidCredentials extends Failure {
      public InvalidCredentials(String message) {
        super(message);
      }

      @Override
      public WebhookSecurityException toException() {
        return new WebhookSecurityException(
            HttpResponseStatus.UNAUTHORIZED.code(), Reason.INVALID_CREDENTIALS, getMessage());
      }
    }

    /** Authorization is valid, but the caller is missing required permissions */
    public static final class Forbidden extends Failure {
      public Forbidden(String message) {
        super(message);
      }

      @Override
      public WebhookSecurityException toException() {
        return new WebhookSecurityException(
            HttpResponseStatus.FORBIDDEN.code(), Reason.FORBIDDEN, getMessage());
      }
    }
  }
}
