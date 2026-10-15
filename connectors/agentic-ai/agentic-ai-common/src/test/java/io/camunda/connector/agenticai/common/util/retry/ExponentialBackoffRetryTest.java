/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExponentialBackoffRetryTest {

  @ParameterizedTest
  @MethodSource("shouldReturnOneSecondDelay")
  void shouldExponentiallyBackoffDelay(int attempt, Duration initialDelay, Duration expectedDelay) {
    assertThat(ExponentialBackoffRetry.delayBeforeAttempt(attempt, initialDelay))
        .isEqualTo(expectedDelay);
  }

  private static Stream<Arguments> shouldReturnOneSecondDelay() {
    return Stream.of(
        argumentSet(
            "attempt 3; initial delay 500ms", 3, Duration.ofMillis(500), Duration.ofSeconds(1)),
        argumentSet("attempt 1; initial delay 1s", 1, Duration.ofSeconds(1), Duration.ofSeconds(1)),
        argumentSet("attempt 2; initial delay 1s", 2, Duration.ofSeconds(1), Duration.ofSeconds(1)),
        argumentSet("attempt 3; initial delay 1s", 3, Duration.ofSeconds(1), Duration.ofSeconds(2)),
        argumentSet("attempt 4; initial delay 1s", 4, Duration.ofSeconds(1), Duration.ofSeconds(4)),
        argumentSet("attempt 5; initial delay 1s", 5, Duration.ofSeconds(1), Duration.ofSeconds(8)),
        argumentSet(
            "attempt 6; initial delay 1s", 6, Duration.ofSeconds(1), Duration.ofSeconds(16)),
        argumentSet(
            "attempt 7; initial delay 1s", 7, Duration.ofSeconds(1), Duration.ofSeconds(32)));
  }
}
