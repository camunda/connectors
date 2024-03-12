package io.camunda.connector.api.error.retry;


import java.time.Duration;
import java.util.Map;

public record RetryContext(
    Map<String, Integer> attemptedRetriesByErrorCode,
    int initialJobRetries,
    Duration initialJobBackoffDuration) {

  public RetryContext incrementAttemptedRetries(String errorCode) {
    attemptedRetriesByErrorCode.compute(errorCode, (k, v) -> v == null ? 0 : v + 1);
    return this;
  }

  public RetryConfig computeNextRetryConfig(
      String errorCode, ConnectorRetryException.RetryPolicy retryPolicy) {
    Integer attemptedRetries = attemptedRetriesByErrorCode.get(errorCode);
    if (attemptedRetries == null) {
      throw new IllegalArgumentException(
          "No retry configuration found for error code " + errorCode);
    }
    return new RetryConfig(
        retryPolicy.retries() - attemptedRetries,
        retryPolicy.getNextBackoffDuration(attemptedRetries));
  }

  public record RetryConfig(int remainingRetries, Duration backoffDuration) {}
}
