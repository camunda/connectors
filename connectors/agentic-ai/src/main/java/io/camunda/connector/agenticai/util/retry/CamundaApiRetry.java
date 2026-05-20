/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamundaApiRetry {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaApiRetry.class);

  private CamundaApiRetry() {}

  /**
   * Executes the given {@code operation}, retrying on retryable failures with exponential backoff.
   *
   * <p>Retries up to {@code maxRetries} times (so up to {@code maxRetries + 1} total attempts).
   * Between attempts the thread sleeps for the duration returned by {@link
   * ExponentialBackoffRetry#delayBeforeAttempt}. Classification of each failure is delegated to
   * {@code classifier}; a {@link ErrorClassifier.Decision#PERMANENT} decision stops retrying
   * immediately.
   *
   * <p>On final failure (retries exhausted, permanent error, or sleep interrupted), the exception
   * is mapped to a {@link ConnectorException} by {@code failureMapper}.
   *
   * @param operation the operation to execute and retry
   * @param classifier decides whether a given exception is retryable
   * @param maxRetries maximum number of retries (0 means try once, no retries)
   * @param initialDelay base delay for the first retry interval
   * @param failureMapper builds the {@link ConnectorException} thrown on final failure
   * @param sleeper controls how the thread sleeps between attempts (use {@link
   *     Sleeper#threadSleep()} in production)
   * @return the value returned by {@code operation} on success
   * @throws ConnectorException when all attempts fail or a permanent error occurs
   */
  public static <T> T execute(
      Supplier<T> operation,
      ErrorClassifier classifier,
      int maxRetries,
      Duration initialDelay,
      FailureMapper failureMapper,
      Sleeper sleeper) {

    final int maxAttempts = maxRetries + 1;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return operation.get();
      } catch (Exception e) {
        final ErrorClassifier.Decision decision = classifier.classify(e);
        if (!decision.isRetryable()) {
          throw failureMapper.toException(e, attempt, FailureReason.PERMANENT_ERROR);
        }
        if (attempt == maxAttempts) {
          throw failureMapper.toException(e, attempt, FailureReason.RETRIES_EXHAUSTED);
        }
        // delay before the next attempt: pass attempt+1 to get the "before attempt N" delay
        final Duration delay =
            ExponentialBackoffRetry.delayBeforeAttempt(attempt + 1, initialDelay);
        LOGGER.warn(
            "Attempt {}/{} failed, retrying in {}ms: {}",
            attempt,
            maxAttempts,
            delay.toMillis(),
            e.getMessage());
        try {
          sleeper.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw failureMapper.toException(ie, attempt, FailureReason.INTERRUPTED);
        }
      }
    }
    throw new IllegalStateException("Unreachable");
  }

  /** Maps a final failure to a {@link ConnectorException}. */
  @FunctionalInterface
  public interface FailureMapper {
    ConnectorException toException(Throwable cause, int attempt, FailureReason reason);
  }

  /** Controls thread sleeping between retry attempts. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    /** Production sleeper using {@link Thread#sleep}. */
    static Sleeper threadSleep() {
      return d -> Thread.sleep(d.toMillis());
    }
  }

  /** The reason a retry sequence gave up. */
  public enum FailureReason {
    /** The classifier returned {@link ErrorClassifier.Decision#PERMANENT}. */
    PERMANENT_ERROR,
    /** All attempts were used up. */
    RETRIES_EXHAUSTED,
    /** The sleep between attempts was interrupted. */
    INTERRUPTED
  }
}
