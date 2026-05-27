/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util.retry;

import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.INTERRUPTED;
import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.PERMANENT_ERROR;
import static io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason.RETRIES_EXHAUSTED;
import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.PERMANENT;
import static io.camunda.connector.agenticai.util.retry.ErrorClassifier.Decision.RETRYABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.util.retry.CamundaApiRetry.FailureReason;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CamundaApiRetryTest {

  private static CamundaApiRetry.FailureMapper simpleMapper() {
    return (cause, attempt, reason) ->
        new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
  }

  private static CamundaApiRetry.Sleeper recordingSleeper(List<Duration> recorded) {
    return d -> recorded.add(d);
  }

  @Test
  void shouldReturnValueOnFirstSuccessfulAttempt() {
    // given
    final List<Duration> recordedSleeps = new ArrayList<>();

    // when
    final String result =
        CamundaApiRetry.execute(
            () -> "ok",
            ErrorClassifier.onAllExceptions(),
            3,
            Duration.ofSeconds(1),
            simpleMapper(),
            recordingSleeper(recordedSleeps));

    // then
    assertThat(result).isEqualTo("ok");
    assertThat(recordedSleeps).isEmpty();
  }

  @Test
  void shouldReturnValueAndSleepBetweenRetriesWhenOperationEventuallySucceeds() {
    // given
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger callCount = new AtomicInteger(0);

    // when
    final String result =
        CamundaApiRetry.execute(
            () -> {
              if (callCount.incrementAndGet() <= 2) {
                throw new RuntimeException("transient");
              }
              return "success";
            },
            ErrorClassifier.onAllExceptions(),
            4,
            Duration.ofSeconds(1),
            simpleMapper(),
            recordingSleeper(recordedSleeps));

    // then
    assertThat(result).isEqualTo("success");
    assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  @Test
  void shouldThrowImmediatelyWithoutSleepWhenClassifierReturnsPermanent() {
    // given
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicReference<Integer> capturedAttempt = new AtomicReference<>();
    final AtomicReference<FailureReason> capturedReason = new AtomicReference<>();
    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt.set(attempt);
          capturedReason.set(reason);
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    // when / then
    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("permanent");
                    },
                    t -> PERMANENT,
                    3,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason.get()).isEqualTo(PERMANENT_ERROR);
    assertThat(capturedAttempt.get()).isEqualTo(1);
    assertThat(recordedSleeps).isEmpty();
  }

  @Test
  void shouldThrowWithCorrectAttemptWhenPermanentErrorOccursAfterRetries() {
    // given
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicInteger classifierCallCount = new AtomicInteger(0);
    final AtomicReference<Integer> capturedAttempt = new AtomicReference<>();
    final AtomicReference<FailureReason> capturedReason = new AtomicReference<>();
    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt.set(attempt);
          capturedReason.set(reason);
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    // when / then
    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("fail");
                    },
                    t -> classifierCallCount.incrementAndGet() <= 2 ? RETRYABLE : PERMANENT,
                    4,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason.get()).isEqualTo(PERMANENT_ERROR);
    assertThat(capturedAttempt.get()).isEqualTo(3);
    assertThat(recordedSleeps).hasSize(2);
  }

  @Test
  void shouldThrowWithRetriesExhaustedReasonWhenAllRetriesFail() {
    // given
    final List<Duration> recordedSleeps = new ArrayList<>();
    final AtomicReference<Integer> capturedAttempt = new AtomicReference<>();
    final AtomicReference<FailureReason> capturedReason = new AtomicReference<>();
    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedAttempt.set(attempt);
          capturedReason.set(reason);
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };

    // when / then
    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("always fail");
                    },
                    ErrorClassifier.onAllExceptions(),
                    4,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    recordingSleeper(recordedSleeps)))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason.get()).isEqualTo(RETRIES_EXHAUSTED);
    assertThat(capturedAttempt.get()).isEqualTo(5);
    assertThat(recordedSleeps)
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8));
  }

  @Test
  void shouldThrowWithInterruptedReasonAndRestoreInterruptFlagWhenSleepIsInterrupted() {
    // given
    final AtomicReference<FailureReason> capturedReason = new AtomicReference<>();
    final AtomicReference<Throwable> capturedCause = new AtomicReference<>();
    final CamundaApiRetry.FailureMapper capturingMapper =
        (cause, attempt, reason) -> {
          capturedReason.set(reason);
          capturedCause.set(cause);
          return new ConnectorException("TEST", "test-" + reason + "-" + attempt, cause);
        };
    final CamundaApiRetry.Sleeper interruptingSleeper =
        d -> {
          throw new InterruptedException("interrupted");
        };

    // when / then
    assertThatThrownBy(
            () ->
                CamundaApiRetry.execute(
                    () -> {
                      throw new RuntimeException("fail");
                    },
                    ErrorClassifier.onAllExceptions(),
                    3,
                    Duration.ofSeconds(1),
                    capturingMapper,
                    interruptingSleeper))
        .isInstanceOf(ConnectorException.class);

    assertThat(capturedReason.get()).isEqualTo(INTERRUPTED);
    assertThat(capturedCause.get()).isInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void shouldClassifyEveryExceptionAsRetryableWhenUsingOnAllExceptions() {
    // given
    final ErrorClassifier classifier = ErrorClassifier.onAllExceptions();

    // then
    assertThat(classifier.classify(new RuntimeException())).isEqualTo(RETRYABLE);
    assertThat(classifier.classify(new Exception())).isEqualTo(RETRYABLE);
    assertThat(classifier.classify(new java.io.IOException())).isEqualTo(RETRYABLE);
  }
}
