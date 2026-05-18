/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

import java.time.Duration;

public final class ExponentialBackoffRetry {

  private ExponentialBackoffRetry() {}

  /**
   * Computes the delay before the given attempt using exponential backoff.
   *
   * <p>The delay is {@code initial * 2^(attempt-2)}, yielding {@code initial} for attempt 2, {@code
   * 2*initial} for attempt 3, {@code 4*initial} for attempt 4, etc.
   *
   * @param attempt the current attempt number (1-based; must be &gt;= 2 to produce a positive
   *     delay)
   * @param initial the base delay for the first retry
   * @return the computed delay duration
   */
  public static Duration delayBeforeAttempt(int attempt, Duration initial) {
    return initial.multipliedBy(
        Math.round(Math.pow(2, attempt - 2))); // 2^0 (x1), 2^1 (x2), 2^2 (x4), ...
  }

  /**
   * Sleeps for the exponential backoff delay before the given attempt.
   *
   * <p>If the thread is interrupted during the sleep, the interrupt flag is restored and a {@link
   * RuntimeException} is thrown.
   *
   * @param attempt the current attempt number (1-based)
   * @param initial the base delay for the first retry
   * @throws RuntimeException if the thread is interrupted while waiting
   */
  public static void waitBeforeAttempt(int attempt, Duration initial) {
    try {
      Thread.sleep(delayBeforeAttempt(attempt, initial));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }
}
