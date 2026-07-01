/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util.retry;

import java.time.Duration;

public final class ExponentialBackoffRetry {

  private ExponentialBackoffRetry() {}

  /**
   * Computes the delay before the given attempt using exponential backoff.
   *
   * <p>The delay is {@code initial * round(2^(attempt-2))}, where {@code round} is {@link
   * Math#round(double)}. This yields:
   *
   * <ul>
   *   <li>attempt 1: {@code initial * 1} (since {@code 2^(-1) = 0.5} rounds to {@code 1})
   *   <li>attempt 2: {@code initial * 1} (since {@code 2^0 = 1})
   *   <li>attempt 3: {@code initial * 2} (since {@code 2^1 = 2})
   *   <li>attempt 4: {@code initial * 4} (since {@code 2^2 = 4})
   * </ul>
   *
   * @param attempt the current attempt number (1-based)
   * @param initial the base delay for the first retry
   * @return the computed delay duration
   */
  public static Duration delayBeforeAttempt(int attempt, Duration initial) {
    return initial.multipliedBy(
        Math.round(Math.pow(2, attempt - 2))); // 2^0 (x1), 2^1 (x2), 2^2 (x4), ...
  }
}
