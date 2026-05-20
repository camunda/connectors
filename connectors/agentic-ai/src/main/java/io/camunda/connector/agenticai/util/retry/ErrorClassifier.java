/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

@FunctionalInterface
public interface ErrorClassifier {

  Decision classify(Throwable t);

  /** Treats every exception as retryable — for callers that do not distinguish error types. */
  static ErrorClassifier onAllExceptions() {
    return t -> Decision.RETRYABLE;
  }

  enum Decision {
    RETRYABLE,
    PERMANENT;

    public boolean isRetryable() {
      return this == RETRYABLE;
    }
  }
}
