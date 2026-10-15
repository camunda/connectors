/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util.retry;

@FunctionalInterface
public interface ErrorClassifier {

  Decision classify(Throwable t);

  /**
   * Returns a classifier that treats every {@link Exception} as retryable. Use for callers that do
   * not need to distinguish error types. Note: {@link Error}s propagate raw from {@link
   * CamundaApiRetry#execute} and are never passed to the classifier.
   */
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
