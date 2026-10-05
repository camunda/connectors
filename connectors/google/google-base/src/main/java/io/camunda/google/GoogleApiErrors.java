/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import io.camunda.connector.api.error.ConnectorException;

/**
 * Turns failures of the Google API client into a {@link ConnectorException}, so the reason Google
 * returned is visible in the incident instead of a generic wrapper message.
 */
public final class GoogleApiErrors {

  private GoogleApiErrors() {}

  public static RuntimeException translate(final RuntimeException exception) {
    if (exception instanceof ConnectorException) {
      return exception;
    }
    final var googleException = findGoogleException(exception);
    if (googleException == null) {
      return exception;
    }

    final var details = googleException.getDetails();
    final var reason =
        details != null && details.getMessage() != null
            ? details.getMessage()
            : googleException.getStatusMessage();
    final var failure =
        "Google API request failed: %d %s".formatted(googleException.getStatusCode(), reason);
    final var wrapperMessage = exception.getMessage();
    final var message =
        wrapperMessage == null
                || exception.getCause() == null
                || wrapperMessage.equals(exception.getCause().toString())
            ? failure
            : wrapperMessage + ": " + failure;
    return new ConnectorException(
        String.valueOf(googleException.getStatusCode()), message, exception);
  }

  private static GoogleJsonResponseException findGoogleException(final Throwable throwable) {
    for (var current = throwable; current != null; current = current.getCause()) {
      if (current instanceof GoogleJsonResponseException googleException) {
        return googleException;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return null;
  }
}
