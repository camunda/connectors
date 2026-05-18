/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExponentialBackoffRetryTest {

  @Test
  void attempt1WithInitialOf1s_returns1s() {
    // 2^(1-2) = 2^(-1) = 0.5 → round(0.5) = 1 → 1 * 1s = 1s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(1, Duration.ofSeconds(1)))
        .isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void attempt2WithInitialOf1s_returns1s() {
    // 2^(2-2) = 2^0 = 1.0 → round(1.0) = 1 → 1 * 1s = 1s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(2, Duration.ofSeconds(1)))
        .isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void attempt3WithInitialOf1s_returns2s() {
    // 2^(3-2) = 2^1 = 2.0 → round(2.0) = 2 → 2 * 1s = 2s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(3, Duration.ofSeconds(1)))
        .isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void attempt4WithInitialOf1s_returns4s() {
    // 2^(4-2) = 2^2 = 4.0 → round(4.0) = 4 → 4 * 1s = 4s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(4, Duration.ofSeconds(1)))
        .isEqualTo(Duration.ofSeconds(4));
  }

  @Test
  void attempt5WithInitialOf1s_returns8s() {
    // 2^(5-2) = 2^3 = 8.0 → round(8.0) = 8 → 8 * 1s = 8s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(5, Duration.ofSeconds(1)))
        .isEqualTo(Duration.ofSeconds(8));
  }

  @Test
  void attempt3WithInitialOf500ms_returns1s() {
    // 2^(3-2) = 2 → 2 * 500ms = 1000ms = 1s
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(3, Duration.ofMillis(500)))
        .isEqualTo(Duration.ofSeconds(1));
  }
}
